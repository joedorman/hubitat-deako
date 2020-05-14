/**
 * 
 *  Copyright 2020 Joseph Dorman
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */


import java.text.DecimalFormat
import groovy.json.JsonSlurper
import static java.util.UUID.randomUUID  


metadata 
{
	definition (name: "Deako Connect", namespace: "joedorman", author: "Joe Dorman")  {
        capability "Initialize"
        capability "Polling"
        attribute "Telnet", ""
        command "childRefresh"
        command "deleteChildren"
        command "ping"
    
	}

	preferences 
	{
		section("Device Settings:") 
		{
	        input name: "ipaddress", type: "string", title: "IP Address of Deako Connect", required: true, displayDuringSetup: true
            input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
            input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
            input name: "PollSchedule", type: "enum", description: "", title: "Poll frequency in min", options: [[1:"1"],[2:"5"],[3:"15"],[4:"30"]], defaultValue: 1
		}
	}
}
def initialize(){
    closeTelnet()
	try {
		if(logEnable) log.debug "Opening telnet connection"
		sendEvent([name: "Telnet", value: "Opening"])
        log.info "Telnet connection opening"
		telnetConnect([terminalType: 'VT100'], "${ipaddress}", 23, null, null)
		//give it a chance to start
		pauseExecution(1000)
		log.info "Telnet connection established"
        sendEvent([name: "Telnet", value: "Open"])
    } catch(e) {
		log.warn "Initialize Error: ${e.message}"
    }
    unschedule()
    runEvery1Minute(pollSchedule);log.info('pollSchedule 1 minute')
}

def installed() {
}

def poll() {
    log.info "Polling"
    forcePoll()
}

def forcePoll(){
	if (logEnable) log.debug "Polling"
	ping()
}

def pollSchedule(){
    forcePoll()
}


void logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

def updated() {
    log.info "updated..."
    log.warn "debug logging is: ${logEnable == true}"
    log.warn "description logging is: ${txtEnable == true}"
    if (logEnable) runIn(1800,logsOff)
	initialize()
}

def telnetStatus(String status) {
	if(logEnable) log.debug "telnetStatus: ${status}"
	if (status == "receive error: Stream is closed" || status == "send error: Broken pipe (Write failed)") {
		log.error("Telnet connection dropped")
        sendEvent([name: "Telnet", value: "Disconnected"])
        initialize()
    }
}

def closeTelnet(){
    telnetClose() 
	unschedule()
}

def childRefresh() {
    log.info "refresh() called"
    //Retrieve Deako Switch Configuration
    getDeviceList() 
}

def getDeviceList() {
    def transactionID = UUID.randomUUID().toString()
    //Get Devices and State from the Deako Connect
    sendMsg('{"transactionId": "' + transactionID + '" ,"type": "DEVICE_LIST","dst": "deako","src": "hubitat"}') 
}

def ping() {
    def transactionID = UUID.randomUUID().toString()
    sendMsg('{"transactionId": "' + transactionID + '","type": "PING","dst": "deako","src": "hubitat"}') 
} 

def sendMsg(String msg) 
{
	if (logEnable) log.info("Sending telnet msg: " + msg)
//	return new hubitat.device.HubAction(msg, hubitat.device.Protocol.TELNET)
    def cmds = []
	cmds << new hubitat.device.HubAction(msg, hubitat.device.Protocol.TELNET)
	sendHubCommand(cmds)
}
    
void parse(String description) {
    log.info "PARSE RAW: " + description
    def json = null;
    try{
        json = new groovy.json.JsonSlurper().parseText(description)
        if(json == null){
            log.warn "String description not parsed"
            return
        }
    }  catch(e) {
        log.error("Failed to parse json e = ${e}")
        return
    }
    if (logEnable) log.debug "Parse: ${json}"
    
    List<Map> defaultValues = []
    if (json?.type == "DEVICE_FOUND") {   
        if (!childExists(json?.data?.uuid)) {
            if (json?.data?.capabilities=="power") {
               addChildDevice("joedorman", "Deako Switch", json?.data?.uuid, [label: json?.data?.name, isComponent: false])
               childDevice = getChildDevice(json?.data?.uuid);
               defaultValues.add([name:"switch", value: powerState(json?.data?.state?.power), descriptionText:"set initial switch value"])
               log.info "Create Switch: " + json?.data?.name + " with power: " + powerState(json?.data?.state?.power)
            } 
            else if (json?.data?.capabilities=="power+dim") {
               addChildDevice("joedorman", "Deako Dimmer", json?.data?.uuid, [label: json?.data?.name, isComponent: false])
               childDevice = getChildDevice(json?.data?.uuid);
               defaultValues.add([name:"switch", value: powerState(json?.data?.state?.power), descriptionText:"set initial switch value"])
               defaultValues.add([name:"level", value: json?.data?.state?.dim , descriptionText:"set initial level value", unit:"%"])
               log.info "Create Dimmer: " + json?.data?.name
            }
        } else {
           childDevice = getChildDevice(json?.data?.uuid);
        } 
    }
    else if (json?.type == "EVENT") {
        log.info "EVENT: " + powerState(json?.data?.state?.power)
        childDevice = getChildDevice(json?.data?.target)
        defaultValues.add([name:"switch", value: powerState(json?.data?.state?.power), descriptionText:"set initial switch value"])
        defaultValues.add([name:"level", value: json?.data?.state?.dim , descriptionText:"set initial level value", unit:"%"])
    }  
    else if (json?.type == "PING") {
        return
    }
    else if (json?.type == "DEVICE_LIST") {
        return
    }
    else if (json?.type == "CONTROL") {
        log.info "CONTROL Chanage was" + json?.status
        return
    }
    
    
    childDevice.parse(defaultValues)
}


def powerState (string) {
    if (string.toString() == "true") {
        return "on"
    } else { 
        return "off"
    }
}

def childExists(uuid) {
    def children = childDevices
    def childDevice = children.find{it.deviceNetworkId.equals(uuid)}
    if (childDevice) 
        return true
    else
        return false  
}

def deleteChildren() {
	log.debug "Parent deleteChildren"
	def children = getChildDevices()
    
    children.each {child->
  		deleteChildDevice(child.deviceNetworkId)
    }
}

    
// device commands
//child device methods
void componentRefresh(cd){
    if (logEnable) log.info "received refresh request from ${cd.displayName}"
}

void componentOn(cd){
    if (logEnable) log.info "received on request from ${cd.displayName}" 
 //   getChildDevice(cd.deviceNetworkId).parse([[name:"switch", value:"on", descriptionText:"${cd.displayName} was turned on"]])
    def transactionID = UUID.randomUUID().toString()
    sendMsg("{\"transactionId\":\"${transactionID}\",\"type\":\"CONTROL\",\"dst\":\"deako\",\"src\":\"acme\",\"data\":{\"target\":\"${cd.deviceNetworkId}\",\"state\":{\"power\":true,\"dim\": 100}}}")
}

void componentOff(cd){
    if (logEnable) log.info "received off request from ${cd.displayName}"
 //   getChildDevice(cd.deviceNetworkId).parse([[name:"switch", value:"off", descriptionText:"${cd.displayName} was turned off"]])
    def transactionID = UUID.randomUUID().toString()
    sendMsg("{\"transactionId\":\"${transactionID}\",\"type\":\"CONTROL\",\"dst\":\"deako\",\"src\":\"acme\",\"data\":{\"target\":\"${cd.deviceNetworkId}\",\"state\":{\"power\":false,\"dim\": 100}}}")
}

void componentSetLevel(cd,level,transitionTime = null) {
    if (logEnable) log.info "received setLevel(${level}, ${transitionTime}) request from ${cd.displayName}"
 //   getChildDevice(cd.deviceNetworkId).parse([[name:"level", value:level, descriptionText:"${cd.displayName} level was set to ${level}%", unit: "%"]])
    def transactionID = UUID.randomUUID().toString()
    sendMsg("{\"transactionId\":\"${transactionID}\",\"type\":\"CONTROL\",\"dst\":\"deako\",\"src\":\"acme\",\"data\":{\"target\":\"${cd.deviceNetworkId}\",\"state\":{\"power\":true,\"dim\":${level}}}")

}

void componentStartLevelChange(cd, direction) {
    if (logEnable) log.info "received startLevelChange(${direction}) request from ${cd.displayName}"
}

void componentStopLevelChange(cd) {
    if (logEnable) log.info "received stopLevelChange request from ${cd.displayName}"
}
