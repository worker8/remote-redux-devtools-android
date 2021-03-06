package com.github.kittinunf.redux.devTools.controller

import com.github.kittinunf.redux.devTools.InstrumentAction
import com.github.kittinunf.redux.devTools.socket.SocketServer
import com.github.kittinunf.redux.devTools.ui.DevToolsPanelComponent
import com.github.kittinunf.redux.devTools.ui.actionButtonDidPressed
import com.github.kittinunf.redux.devTools.ui.backwardButtonDidPressed
import com.github.kittinunf.redux.devTools.ui.forwardButtonDidPressed
import com.github.kittinunf.redux.devTools.ui.timeSliderValueDidChanged
import com.github.kittinunf.redux.devTools.util.R
import com.github.kittinunf.redux.devTools.util.addTo
import com.github.kittinunf.redux.devTools.util.gson
import com.github.kittinunf.redux.devTools.util.resource
import com.github.kittinunf.redux.devTools.state.DevToolsTimeLineAction
import com.github.kittinunf.redux.devTools.state.DevToolsTimeLineState
import com.github.kittinunf.redux.devTools.state.DevToolsTimeLineState.Companion.reduce
import rx.Observable
import rx.schedulers.SwingScheduler
import rx.subscriptions.CompositeSubscription
import java.util.concurrent.TimeUnit
import javax.swing.ImageIcon
import javax.swing.JSlider

class DevToolsTimeLineController(component: DevToolsPanelComponent) {

    private val initialMaxValue = 0

    private val subscriptionBag = CompositeSubscription()

    init {
        val resetCommand = SocketServer.messages.map { gson.fromJson(it, InstrumentAction::class.java) }
                .ofType(InstrumentAction.Init::class.java)
                .map { DevToolsTimeLineAction.Reset(initialMaxValue) }

        val forwardCommand = component.forwardButtonDidPressed().map { DevToolsTimeLineAction.Forward }

        val backwardCommand = component.backwardButtonDidPressed().map { DevToolsTimeLineAction.Backward }

        val playOrPauseCommand = component.actionButtonDidPressed().map { DevToolsTimeLineAction.PlayOrPause }

        val setValueCommand = component.timeSliderValueDidChanged().map { DevToolsTimeLineAction.SetToValue((it.source as JSlider).value) }

        val adjustMaxAndSetToMaxCommand = SocketServer.messages.map { gson.fromJson(it, InstrumentAction::class.java) }
                .ofType(InstrumentAction.SetState::class.java)
                .filter {
                    val payload = it.payload
                    !payload.reachMax
                }
                .concatMap {
                    Observable.from(listOf(DevToolsTimeLineAction.AdjustMax, DevToolsTimeLineAction.SetToMax))
                }

        val states = Observable.merge(resetCommand,
                forwardCommand,
                backwardCommand,
                playOrPauseCommand,
                setValueCommand,
                adjustMaxAndSetToMaxCommand)
                .scan(DevToolsTimeLineState(maxValue = initialMaxValue), ::reduce)
                .replay(1)
                .autoConnect()

        component.actionButtonDidPressed()
                .withLatestFrom(states.map { it.playState }) { _, state -> state }
                //from pause state
                .filter { it == DevToolsTimeLineState.PlayState.PAUSE }
                .subscribe {
                    Observable.fromCallable { component.timeLineForwardButton.doClick() }
                            .subscribeOn(SwingScheduler.getInstance())
                            .repeatWhen { it.delay(800, TimeUnit.MILLISECONDS) }
                            .takeUntil(component.actionButtonDidPressed())
                            .publish()
                            .connect()
                }
                .addTo(subscriptionBag)

        //play or pause
        states.map { if (it.playState == DevToolsTimeLineState.PlayState.PLAY) resource(R.pause) else resource(R.play) }
                .distinctUntilChanged()
                .observeOn(SwingScheduler.getInstance())
                .subscribe {
                    component.timeLineActionButton.icon = ImageIcon(it)
                }
                .addTo(subscriptionBag)

        //timeline slider
        states.map { (it.value to it.maxValue) }
                .observeOn(SwingScheduler.getInstance())
                .subscribe {
                    component.timeLineTimeSlider.model.valueIsAdjusting = true
                    component.timeLineTimeSlider.maximum = it.second
                    component.timeLineTimeSlider.value = it.first
                }
                .addTo(subscriptionBag)

        //backward button
        states.map { it.backwardEnabled }
                .distinctUntilChanged()
                .observeOn(SwingScheduler.getInstance())
                .subscribe { component.timeLineBackwardButton.isEnabled = it }
                .addTo(subscriptionBag)

        //forward button
        states.map { it.forwardEnabled }
                .distinctUntilChanged()
                .observeOn(SwingScheduler.getInstance())
                .subscribe { component.timeLineForwardButton.isEnabled = it }
                .addTo(subscriptionBag)

        //notify client
        states.filter { it.shouldNotifyClient }
                .map { it.value }
                .distinctUntilChanged()
                .subscribe {
                    val json = gson.toJson(InstrumentAction.JumpToState(it), InstrumentAction::class.java)
                    SocketServer.send(json)
                }
                .addTo(subscriptionBag)
    }
}
