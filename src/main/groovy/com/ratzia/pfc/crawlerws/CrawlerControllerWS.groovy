/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.ratzia.pfc.crawlerws
//@GrabExclude('stax#stax-api;1.0.1!stax-api.jar')
//@Grab(group='org.codehaus.groovy.modules', module='groovyws', version='0.5.2')
import groovyx.net.ws.WSServer
/**
 *
 * @author frans
 */
class CrawlerControllerWS {
    CrawlerControllerWS(){
        def server = new WSServer()
        CrawlerControllerWebService eo = new CrawlerControllerWebService()
        server.setNode("com.ratzia.pfc.crawlerws.CrawlerControllerWebService", "http://localhost:6981/CrawlerControllerWebService")
        server.start()	
    }
}

