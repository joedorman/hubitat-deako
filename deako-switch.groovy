/*
    
	Converted from: Generic Component Dimmer

    https://raw.githubusercontent.com/hubitat/HubitatPublic/master/examples/drivers/genericComponentDimmer.groovy

*/
metadata {
    definition(name: "Deako Switch", namespace: "joedorman", author: "joe dorman", component: true) {
        capability "Light"
        capability "Switch"
        capability "Refresh"
        capability "Actuator"
    }
    preferences {
        input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
    }
}

void updated() {
    log.info "Updated..."
    log.warn "description logging is: ${txtEnable == true}"
}

void installed() {
    log.info "Installed..."
    device.updateSetting("txtEnable",[type:"bool",value:true])
    refresh()
}

void parse(String description) { log.warn "parse(String description) not implemented" }

void parse(List<Map> description) {
    description.each {
        if (it.name in ["switch","level"]) {
            if (txtEnable) log.info it.descriptionText
            sendEvent(it)
        }
    }
}

void on() {
    parent?.componentOn(this.device)
}

void off() {
    parent?.componentOff(this.device)
}

void refresh() {
    parent?.componentRefresh(this.device)
}
