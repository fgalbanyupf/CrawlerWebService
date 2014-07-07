/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.ratzia.pfc.crawlerws;

import edu.uci.ics.crawler4j.crawler.CrawlConfig;
import edu.uci.ics.crawler4j.crawler.CrawlController;
import edu.uci.ics.crawler4j.crawler.WebCrawler;
import edu.uci.ics.crawler4j.fetcher.PageFetcher;
import edu.uci.ics.crawler4j.robotstxt.RobotstxtServer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Creates Crawler threads, this subclass includes session and jail managements for each child process
 * @author frans
 */
public class SessionCrawlController extends CrawlController {
    private long sessionId;  
    private Pattern jail;
    public SessionCrawlController(CrawlConfig config, PageFetcher pageFetcher, RobotstxtServer robotstxtServer, long sessionId, Pattern jail) throws Exception {
        super(config, pageFetcher, robotstxtServer);
        this.sessionId = sessionId;
        this.jail = jail;
    }

    protected <T extends WebCrawler> void start(final Class<T> _c, final int numberOfCrawlers, boolean isBlocking) {
        try {
            finished = false;
            crawlersLocalData.clear();
            final List<Thread> threads = new ArrayList();
            final List<T> crawlers = new ArrayList();

            for (int i = 1; i <= numberOfCrawlers; i++) {
                T crawler = _c.newInstance();
                Thread thread = new Thread(crawler, "Crawler " + i);
                crawler.setThread(thread);
                crawler.init(i, this);
                //Set session ID
                ((Crawler)crawler).setSessionId(sessionId);
                //Set jail
                ((Crawler)crawler).setJailRegexp(jail);
                thread.start();
                crawlers.add(crawler);
                threads.add(thread);
            }

            final CrawlController controller = this;

            Thread monitorThread = new Thread(new Runnable() {

                //@Override
                public void run() {
                    try {
                        synchronized (waitingLock) {

                            while (true) {
                                sleep(10);
                                boolean someoneIsWorking = false;
                                for (int i = 0; i < threads.size(); i++) {
                                    Thread thread = threads.get(i);
                                    if (!thread.isAlive()) {
                                        if (!shuttingDown) {
                                            T crawler = _c.newInstance();
                                            thread = new Thread(crawler, "Crawler " + (i + 1));
                                            threads.remove(i);
                                            threads.add(i, thread);
                                            crawler.setThread(thread);
                                            crawler.init(i + 1, controller);
                                             //Set session ID
                                            ((Crawler)crawler).setSessionId(sessionId);
                                            //Set jail
                                            ((Crawler)crawler).setJailRegexp(jail);
                                            thread.start();
                                            crawlers.remove(i);
                                            crawlers.add(i, crawler);
                                        }
                                    } else if (crawlers.get(i).isNotWaitingForNewURLs()) {
                                        someoneIsWorking = true;
                                    }
                                }
                                if (!someoneIsWorking) {
									// Make sure again that none of the threads
                                    // are
                                    // alive.
                                    sleep(10);

                                    someoneIsWorking = false;
                                    for (int i = 0; i < threads.size(); i++) {
                                        Thread thread = threads.get(i);
                                        if (thread.isAlive() && crawlers.get(i).isNotWaitingForNewURLs()) {
                                            someoneIsWorking = true;
                                        }
                                    }
                                    if (!someoneIsWorking) {
                                        if (!shuttingDown) {
                                            long queueLength = frontier.getQueueLength();
                                            if (queueLength > 0) {
                                                continue;
                                            }
                                            sleep(10);
                                            queueLength = frontier.getQueueLength();
                                            if (queueLength > 0) {
                                                continue;
                                            }
                                        }

										// At this step, frontier notifies the
                                        // threads that were
                                        // waiting for new URLs and they should
                                        // stop
                                        frontier.finish();
                                        for (T crawler : crawlers) {
                                            crawler.onBeforeExit();
                                            crawlersLocalData.add(crawler.getMyLocalData());
                                        }

                                        sleep(10);

                                        frontier.close();
                                        docIdServer.close();
                                        pageFetcher.shutDown();

                                        finished = true;
                                        waitingLock.notifyAll();

                                        return;
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });

            monitorThread.start();

            if (isBlocking) {
                waitUntilFinish();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
