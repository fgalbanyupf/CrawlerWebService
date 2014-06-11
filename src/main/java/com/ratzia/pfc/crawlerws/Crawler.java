/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.ratzia.pfc.crawlerws;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import edu.uci.ics.crawler4j.crawler.Page;
import edu.uci.ics.crawler4j.crawler.WebCrawler;
import edu.uci.ics.crawler4j.parser.HtmlParseData;
import edu.uci.ics.crawler4j.url.WebURL;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 *
 * @author frans
 */
public class Crawler extends WebCrawler {

        private final static Pattern FILTERS = Pattern.compile(".*(\\.(css|js|bmp|gif|jpe?g" + "|png|tiff?|mid|mp2|mp3|mp4"
                        + "|wav|avi|mov|mpeg|ram|m4v|pdf" + "|rm|smil|wmv|swf|wma|zip|rar|gz))$");
        
      
        private static DBCollection dbCol;
        //FIXME: We have to remove static, it doesn't work when working with more than one session at a time.
        //       Having a local sessionId and copying at onStart is not thread proof
        private static long sessionId;  
        private static Pattern jail;

        public static void setDB(DB db, String collection) {
                dbCol = db.getCollection(collection);
        }
        
        public static void setSessionId(long sessionId) {
                Crawler.sessionId = sessionId;
        }
        
        public static void setJailRegexp(Pattern jailRegexp) {
            Crawler.jail = jailRegexp;
        }
        @Override
        public void onStart()  {
            super.onStart();
        }

        /**
         * This function is called just before the termination of the current
         * crawler instance. It can be used for persisting in-memory data or other
         * finalization tasks.
         */
        @Override
        public void onBeforeExit() {
            super.onBeforeExit();
        }
        
        
        /**
         * You should implement this function to specify whether the given url
         * should be crawled or not (based on your crawling logic).
         */
        @Override
        public boolean shouldVisit(WebURL url) {
                String href = url.getURL().toLowerCase();
                return !FILTERS.matcher(href).matches() && jail.matcher(href).matches();
        }
        
        

        /**
         * This function is called when a page is fetched and ready to be processed
         * by your program.
         */
        @Override
        public void visit(Page page) {
                int docid = page.getWebURL().getDocid();
                String url = page.getWebURL().getURL();
                System.out.println(url);
                /*
                //String domain = page.getWebURL().getDomain();
                "domain": "co.nz",
                "url": "http://www.xxxxxx.co.nz/foo",
                 */
                String domain = "";
                try {
                    domain = (new URI(url)).getHost();
                } catch (URISyntaxException ex) {
                    Logger.getLogger(Crawler.class.getName()).log(Level.SEVERE, null, ex);
                }
                
                String parentUrl = page.getWebURL().getParentUrl();
                
                if (page.getParseData() instanceof HtmlParseData) {
                        HtmlParseData htmlParseData = (HtmlParseData) page.getParseData();
                        String text = htmlParseData.getText();
                        String html = htmlParseData.getHtml();
                        //List<WebURL> links = htmlParseData.getOutgoingUrls();


                        //TODO: Codificar el text per fer-lo BSON compatible en casos extrems de codificaicó
                        BasicDBObject doc = new BasicDBObject("docid", docid).
                                                    append("domain", domain).
                                                    append("url", url).
                                                    append("parentUrl", parentUrl).
                                                    append("charset", page.getContentCharset()).
                                                    append("dateAdd",  new Date()).
                                                    append("ver", 0).
                                                    append("html", html).
                                                    append("session_id",sessionId); //FIXME: sessionId unstatiquize :/
                        dbCol.insert(doc);
                }
        }
}

//grep "<link r:resource=\".*\"><\/link>" content.rdf.u8 | sed -E 's/^.*<link r:resource=\"(.*)\"><\/link>/\1/p'
