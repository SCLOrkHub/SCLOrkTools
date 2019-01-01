SCLOrkClockProxyFollower {
	var <clock;
	var serverNetAddr;
	var listenPort;

	var controlOscFunc;

	*new { | clock, serverNetAddr, listenPort = 7702 |
		^super.newCopyArgs(clock, serverNetAddr, listenPort).init;
	}

	init {
		// message format: verb, beat64hi, beat64low, [ optional args ]
		controlOscFunc = OSCFunc.new({ | msg, time, addr |
			var verb, beat;
			[ "client", msg, time, addr ].postln;
			verb = msg[1];
			beat = Float.from64Bits(msg[2], msg[3]);
			// Different behavior depending on if beat is for *now* or
			// for some future date.
			if (beat <= 0.0, {
				switch (verb,
					\stop, { this.stop(); },
					\tempo, {
						clock.tempo = Float.from64Bits(msg[4], msg[5]);
					},
					\permanent, {
						this.permanent_ = msg[4];
					},
					\beatsPerBar, {
						var newMeter = Float.from64Bits(msg[4], msg[5]);
						clock.sched(0, { clock.beatsPerBar = newMeter });
					},
					\fadeTempo, {
					/* TODO */
					},
					/* default */ {
						"Invalid verb sent to ProxyClockClient.".postln;
				});
			}, {
				switch (verb,
					\stop, { clock.schedAbs(beat, { this.stop(); }); },
					\tempo, {
						var newTempo = Float.from64Bits(msg[4], msg[5]);
						clock.setTempoAtBeat(beat, newTempo);
					},
					\tempoAtSec, {
						var newTempo = Float.from64Bits(msg[4], msg[5]);
						clock.setTempoAtSec(beat, newTempo);
					},
					\beatsPerBar, {
						var newMeter = Float.from64Bits(msg[4], msg[5]);
						clock.schedAbs(beat, {
							clock.beatsPerBar = newMeter;
						});
					},
					\fadeTempo, {
						/* TODO */
					},
					/* default */ {
						"Invalid verb sent to ProxyClockClient.".postln;
				});
			});
		},
		path: '/proxyClockControl',
		recvPort: listenPort
		);

		// Register with the server.
		serverNetAddr.sendMsg('/registerClock', \add, listenPort);
	}

	free {
		serverNetAddr.sendMsg('/registerClock', \remove, listenPort);
	}

	stop {
		clock.stop;
	}
}