package com.github.kittinunf.redux.devTools.controller

import com.github.kittinunf.redux.devTools.socket.SocketServer
import com.github.kittinunf.redux.devTools.ui.DevToolsPanelComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * Created by kittinunf on 8/16/16.
 */

class DevToolsWindowFactory : ToolWindowFactory {

    val component: DevToolsPanelComponent

    val monitorController: DevToolsMonitorController
    val timeLineController: DevToolsTimeLineController

    init {
        component = DevToolsPanelComponent()
        monitorController = DevToolsMonitorController(component)
        timeLineController = DevToolsTimeLineController(component)
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentFactory = ContentFactory.SERVICE.getInstance()
        val content = contentFactory.createContent(component.devToolsPanel, "", false)
        toolWindow.contentManager.addContent(content)

        if (!SocketServer.hasStarted) SocketServer.start()
    }

}