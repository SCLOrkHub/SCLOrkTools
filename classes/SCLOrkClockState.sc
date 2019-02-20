SCLOrkClockState {
	var <>cohortName;
	var <>applyAtBeat;
	var <>tempo;
	var <>beatsPerBar;
	var <>baseBar;
	var <>baseBarBeat;

	var <>applyAtTime;

	*new { |
		cohortName = 'default',
		applyAtBeat = 0.0,
		tempo = 1.0,
		beatsPerBar = 4.0,
		baseBar = 0.0,
		baseBarBeat = 0.0 |
		^super.newCopyArgs(cohortName, applyAtBeat, tempo, beatsPerBar, baseBar, baseBarBeat).init;
	}

	*newFromMessage { | msg |
		var cohortName, applyAtBeat, tempo, beatsPerBar, baseBar, baseBarBeat;
		// Ignore first element in msg, assumed to be OSC path.
		cohortName = msg[1];
		applyAtBeat = Float.from64Bits(msg[2], msg[3]);
		tempo = Float.from64Bits(msg[4], msg[5]);
		beatsPerBar = Float.from64Bits(msg[6], msg[7]);
		baseBar = Float.from64Bits(msg[8], msg[9]);
		baseBarBeat = Float.from64Bits(msg[10], msg[11]);
		^SCLOrkClockState.new(cohortName, applyAtBeat, tempo, beatsPerBar, baseBar, baseBarBeat);
	}

	init {
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

	beats2secs { | beats |
		^(applyAtTime + ((beats - applyAtBeat) / tempo));
	}

	secs2beats { | secs |
		^(applyAtBeat + (tempo * (secs - applyAtTime)));
	}

	toMessage {
		var msg = Array.newClear(12);
		msg[0] = nil;   // leave blank for sender to populate
		msg[1] = cohortName;
		msg[2] = applyAtBeat.high32Bits;
		msg[3] = applyAtBeat.low32Bits;
		msg[4] = tempo.high32Bits;
		msg[5] = tempo.low32Bits;
		msg[6] = beatsPerBar.high32Bits;
		msg[7] = beatsPerBar.low32Bits;
		msg[8] = baseBar.high32Bits;
		msg[9] = baseBar.low32Bits;
		msg[10] = baseBarBeat.high32Bits;
		msg[11] = baseBarBeat.low32Bits;
		^msg;
	}
}