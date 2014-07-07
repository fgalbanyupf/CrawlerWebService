/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.ratzia.pfc.crawlerws

import com.sleepycat.je.EnvironmentConfig
import edu.uci.ics.crawler4j.crawler.CrawlConfig
import edu.uci.ics.crawler4j.crawler.CrawlController
import edu.uci.ics.crawler4j.fetcher.PageFetcher
import edu.uci.ics.crawler4j.fetcher.PageFetcher
import edu.uci.ics.crawler4j.robotstxt.RobotstxtConfig
import edu.uci.ics.crawler4j.robotstxt.RobotstxtConfig
import edu.uci.ics.crawler4j.robotstxt.RobotstxtServer
import edu.uci.ics.crawler4j.robotstxt.RobotstxtServer

import com.mongodb.BasicDBObject
import com.mongodb.DB
import com.mongodb.DBCollection
import com.mongodb.MongoClient

import java.util.regex.Pattern;

/**
 * Web service instance
 * @author frans
 */
class CrawlerControllerWebService {
    private String dbAddress = "localhost"
    private String dbName = "crawler"
    private String dbCollection = "pages"
    private String dbSessionCollection = "sessions"
    private String frontiersBasePath = "/tmp/frontier/"
    private final String SETTINGSFILE = "settings.properties"
    CrawlerControllerWebService(){ 
        //Load config file values
        if(new File(SETTINGSFILE).exists()){
            def config = new ConfigSlurper().parse(new File(SETTINGSFILE).toURL())
            this.dbAddress = config.dbAddress
            this.dbName = config.dbName
            this.dbCollection = config.dbCollection
            this.dbSessionCollection = config.dbSessionCollection
            this.frontiersBasePath = config.frontiersBasePath
            println "Settings loaded " + config
        }else{
            println "No settings file found, using default values instead"
        }
    }
    public String test(String testString) {
        return "Test " + testString
    }
        
    public startCrawler(long sessionId, String jail, int numCrawlers, int depth, String seed){
        //Seed is added as a special case in the jail
        jail = jail + "|" + seed
        
        //TODO: Limitar el nombre de threads
        def th = Thread.start {
            EnvironmentConfig envConfig = new EnvironmentConfig();

            MongoClient mongoClient;
            DB db;
            DBCollection sessionCol;

            try {
                mongoClient = new MongoClient( dbAddress )
                db = mongoClient.getDB( dbName )
                Crawler.setDB(db,dbCollection)
                //Crawler.setSessionId(sessionId)
                //Crawler.setJailRegexp(Pattern.compile(jail))
                sessionCol = db.getCollection(dbSessionCollection)
            } catch (UnknownHostException ex) {
                Logger.getLogger(CrawlerController.class.getName()).log(Level.SEVERE, null, ex);
                System.out.println("No s'ha pogut connectar a la base de dades!!");
                return;
            }
            /*********************/

            String crawlStorageFolder = frontiersBasePath + sessionId;
            CrawlConfig config = new CrawlConfig();
            config.setCrawlStorageFolder(crawlStorageFolder);
            config.setResumableCrawling(true);
            config.setPolitenessDelay(100);
            config.setMaxDepthOfCrawling(depth);
            config.setMaxOutgoingLinksToFollow(50000);
            config.setMaxDownloadSize(1048576); //1 Mb
            /*
             * Instantiate the controller for this crawl.
             */
            PageFetcher pageFetcher = new PageFetcher(config);
            RobotstxtConfig robotstxtConfig = new RobotstxtConfig();
            robotstxtConfig.setEnabled(true);
            //robotstxtConfig.setCacheSize(0);
            RobotstxtServer robotstxtServer = new RobotstxtServer(robotstxtConfig, pageFetcher);

            SessionCrawlController controller;
            try {
                controller = new SessionCrawlController(config, pageFetcher, robotstxtServer, sessionId, Pattern.compile(jail));
            } catch (Exception ex) {
                Logger.getLogger(CrawlerController.class.getName()).log(Level.SEVERE, null, ex);
                println("No s'ha pogut iniciar el crawler");
                return;
            }
            
            //Crawling sessions field (Controlling and IPC)
            BasicDBObject doc = sessionCol.findOne(new BasicDBObject("session_id", sessionId));
            if(doc){
                //FIXME: Not thread safe
                //Update (restart)
                if(doc.get("status") == "canceled"){
                    doc.put("status","running")
                    doc.put("shouldStop",false)
                    sessionCol.save(doc)
                }else{
                    println("Crawler is already running or finished");
                    throw new IllegalArgumentException("Crawler is already running or finished"); 
                }
            }else{
                doc = new BasicDBObject("session_id",sessionId).append("shouldStop",false).append("status","running");
                sessionCol.insert(doc);
            }
            
            controller.addSeed(seed);
            controller.startNonBlocking(Crawler.class, numCrawlers); 
            Thread.sleep(30 * 1000);
            
            Boolean exitCrawling = false;
            while(!exitCrawling && !controller.isFinished()){
                Boolean stopSession=false;
                sessionCol.findOne()
                BasicDBObject session = sessionCol.findOne(new BasicDBObject("session_id", sessionId));
                if(session){
                    stopSession = session.getBoolean("shouldStop")
                    if(stopSession){
                        session.put("status","stopping")
                        sessionCol.save(session)
                    }
                }else{
                    //Deleted...
                    stopSession = true
                }
                if(stopSession){
                    println "Aturant crawler!!"
                    
                    controller.shutdown()
                    exitCrawling = true
                }
                Thread.sleep(30 * 1000);
            }
            if(exitCrawling){
                println "Crawling cancel·led"
                controller.waitUntilFinish();
            }
            
            BasicDBObject session = sessionCol.findOne(new BasicDBObject("session_id", sessionId));
            if(session){
                if(exitCrawling){
                    session.put("status","canceled")
                }else{
                    session.put("status","finished")
                }
                sessionCol.save(session)
            }else{
                //Deleted session, purge pages
                DBCollection collection = db.getCollection(dbCollection)
                collection.remove(new BasicDBObject("session_id", sessionId));
                
            }


            println "CrawlerJob FI"
        }
    } 
        
    //Throws several exceptions, we want the webservice call to crash if something failed
    public String crawlerStatus(long sessionId){
        MongoClient mongoClient;
        DB db;
        DBCollection sessionCol;

        mongoClient = new MongoClient( dbAddress )
        db = mongoClient.getDB( dbName )
        sessionCol = db.getCollection(dbSessionCollection)
            
            
        BasicDBObject session = sessionCol.findOne(new BasicDBObject("session_id", sessionId));
        return session.get("status") //We want it to crash if there is no matching session_id
    }
    
    //Throws several exceptions, we want the webservice call to crash if something failed
    public long crawlerPageCount(long sessionId){
        MongoClient mongoClient;
        DB db;
        DBCollection col;

        mongoClient = new MongoClient( dbAddress )
        db = mongoClient.getDB( dbName )
        col = db.getCollection(dbCollection)
            
        return col.find(new BasicDBObject("session_id", sessionId)).count();
    }
        
    //Throws several exceptions, we want the webservice call to crash if something failed
    public stopCrawler(long sessionId){
        MongoClient mongoClient;
        DB db;
        DBCollection sessionCol;

        mongoClient = new MongoClient( dbAddress )
        db = mongoClient.getDB( dbName )
        sessionCol = db.getCollection(dbSessionCollection)
            
            
        BasicDBObject session = sessionCol.findOne(new BasicDBObject("session_id", sessionId));
        if(session){
            session.put("shouldStop",true)
            sessionCol.save(session)
        }
    }
    
    public deleteSession(long sessionId){
        MongoClient mongoClient;
        DB db;
        DBCollection sessionCol;
        DBCollection collection;

        mongoClient = new MongoClient( dbAddress )
        db = mongoClient.getDB( dbName )
        //Delete session
        sessionCol = db.getCollection(dbSessionCollection)
        sessionCol.remove(new BasicDBObject("session_id", sessionId));
        
        //Delete pages
        collection = db.getCollection(dbCollection)
        collection.remove(new BasicDBObject("session_id", sessionId));
    }
    
    /*'sessionId':crawlerSessionInstance.id,\
    "jail":crawlerSessionInstance.jail,
    "numCrawlers":crawlerSessionInstance.numCrawlers,
    "depth":crawlerSessionInstance.depth,
    "seed":crawlerSessionInstance.seed,*/
}

