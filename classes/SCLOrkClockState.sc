SCLOrkClockState {
	var <>cohortName;
	var <>applyAtBeat;
	var <>applyAtTime;  // idea: always keep applyAtTime as server-side time
	var <>tempo;
	var <>beatsPerBar;
	var <>baseBar;
	var <>baseBarBeat;

	*new { |
		cohortName = 'default',
		applyAtBeat = 0.0,
		applyAtTime = nil,  // will be thisThread.seconds if left nil
		tempo = 1.0,
		beatsPerBar = 4.0,
		baseBar = 0.0,
		baseBarBeat = 0.0 |
		^super.newCopyArgs(
			cohortName,
			applyAtBeat,
			applyAtTime,
			tempo,
			beatsPerBar,
			baseBar,
			baseBarBeat).init;
	}

	*newFromMessage { | msg |
		// Ignore first element in msg, assumed to be OSC path.
		var cohortName = msg[1];
		var applyAtBeat = Float.from64Bits(msg[2], msg[3]);
		var applyAtTime = Float.from64Bits(msg[4], msg[5]);
		var tempo = Float.from64Bits(msg[6], msg[7]);
		var beatsPerBar = Float.from64Bits(msg[8], msg[9]);
		var baseBar = Float.from64Bits(msg[10], msg[11]);
		var baseBarBeat = Float.from64Bits(msg[12], msg[13]);
		^SCLOrkClockState.new(
			cohortName,
			applyAtBeat,
			applyAtTime,
			tempo,
			beatsPerBar,
			baseBar,
			baseBarBeat);
	}

	init {
		if (applyAtTime.isNil, {
			applyAtTime = thisThread.seconds;
		});
	}

	beatDur {
		^(1.0 / tempo);
	}

	beats2bars { | beats |
		^(baseBar + ((beats - baseBarBeat) / beatsPerBar)).trunc;
	}

	bars2beats { | bars |
		^(baseBarBeat + ((bars - baseBar) * beatsPerBar));
	}

	beats2secs { | beats, timeDiff = 0.0 |
		^(applyAtTime + timeDiff + ((beats - applyAtBeat) / tempo));
	}

	secs2beats { | secs, timeDiff = 0.0 |
		^(applyAtBeat + (tempo * (secs - (applyAtTime + timeDiff))));
	}

	toMessage { | timeOverride |
		var msg = Array.newClear(14);
		msg[0] = nil;   // Leave blank for sender to populate
		msg[1] = cohortName;
		msg[2] = applyAtBeat.high32Bits;
		msg[3] = applyAtBeat.low32Bits;
		if (timeOverride.notNil, {
			msg[4] = timeOverride.high32Bits;
			msg[5] = timeOverride.low32Bits;
		}, {
			msg[4] = applyAtTime.high32Bits;
			msg[5] = applyAtTime.low32Bits;
		});
		msg[6] = tempo.high32Bits;
		msg[7] = tempo.low32Bits;
		msg[8] = beatsPerBar.high32Bits;
		msg[9] = beatsPerBar.low32Bits;
		msg[10] = baseBar.high32Bits;
		msg[11] = baseBar.low32Bits;
		msg[12] = baseBarBeat.high32Bits;
		msg[13] = baseBarBeat.low32Bits;
		^msg;
	}
}