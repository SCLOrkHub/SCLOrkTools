SCLOrkClockProxyLeader {
	var <clock;
	var listenPort;

	var clientNetAddrSet;
	var <permanent;

	var registerOscFunc;


	*new { | clock, listenPort = 7703 |
		^super.newCopyArgs(clock, listenPort).init;
	}

	init {
		clientNetAddrSet = Set.new;

		// message format: verb (\add or \remove), port.
		registerOscFunc = OSCFunc.new({ | msg, time, addr |
			var verb, port, netAddr;
			["server", msg, time, addr].postln;
			verb = msg[1];
			port = msg[2];
			netAddr = NetAddr.new(addr.ip, port);
			if (verb == \add, {
				"adding".postln;
				clientNetAddrSet.add(netAddr);
				// Send current tempo and meter values to the new
				// client.
				netAddr.sendRaw(this.prTempoMessage(0.0, clock.tempo));
				netAddr.sendRaw(
					this.prBeatsPerBarMessage(0.0, clock.beatsPerBar));
			}, {
				// verb is assumed to be \remove.
				clientNetAddrSet.remove(netAddr);
			});
		},
		path: '/registerClock',
		recvPort: listenPort
		);
	}

	stop {
		this.prSendAll(this.prStopMessage(0.0));
		clock.stop;
	}

	stopAtBeat { | beat |
		this.prSendAll(this.prStopMessage(beat));
		clock.schedAbs(beat, { clock.stop; });
	}

	tempo {
		^clock.tempo;
	}

	tempo_ { | newTempo |
		this.prSendAll(this.prTempoMessage(0.0, newTempo));
		clock.tempo = newTempo;
	}

	permanent_ { | val |
		/* TODO */
	}

	beatDur {
		^clock.beatDur;
	}

	beatsPerBar {
		^clock.beatsPerBar;
	}

	beatsPerBar_ { | newMeter |
		this.prSendAll(this.prBeatsPerBarMessage(0.0, newMeter));
		clock.sched(0, { clock.beatsPerBar_ = newMeter; });
	}

	setMeterAtBeat { | newMeter, beats |
		this.prSendAll(this.prBeatsPerBarMessage(beats, newMeter));
		clock.setMeterAtBeat(newMeter, beats);
	}

	setTempoAtBeat { | newTempo, beats |
		this.prSendAll(this.prTempoMessage(beats, newTempo));
		clock.setTempoAtBeat(newTempo, beats);
	}

	prSendAll { | msgArray |
		clientNetAddrSet.do({ | netAddr, index |
			netAddr.sendRaw(msgArray);
		});
	}

	prStopMessage { | beat |
		^['/proxyClockControl',
			\stop,
			beat.high32Bits,
			beat.low32Bits].asRawOSC;
	}

	prTempoMessage { | beat, newTempo |
		^['/proxyClockControl',
			\tempo,
			beat.high32Bits,
			beat.low32Bits,
			newTempo.high32Bits,
			newTempo.low32Bits].asRawOSC;
	}

	prBeatsPerBarMessage { | beat, newMeter |
		^['/proxyClockControl',
			\beatsPerBar,
			beat.high32Bits,
			beat.low32Bits,
			newMeter.high32Bits,
			newMeter.low32Bits].asRawOSC;
	}
}