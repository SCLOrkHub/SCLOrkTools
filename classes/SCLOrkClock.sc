SCLOrkClock : TempoClock {
	const beatsUpdateInterval = 1.0;
	var timeClient;
	var beatsSyncTask;

	*new { | timeClient, tempo, beats, seconds, queueSize = 256 |
		^super.new(tempo, beats, seconds, queueSize).setVars(timeClient).init;
	}

	setVars { | timeClient |
		timeClient = timeClient;
	}

	init {
		super.beats_((Main.elapsedTime + timeClient.timeDiff) / this.tempo);

		beatsSyncTask = Task.new({
			while ({ true }, {
				beatsUpdateInterval.wait;
				super.beats_((Main.elapsedTime + timeClient.timeDiff) / this.tempo);
			});
		}, SystemClock).start;

	}

	beats {
		^super.beats;
	}

	beats_ { | newBeats |
		"Note: SCLOrkClock ignores manual setting of beats parameter.".postln;
	}

	seconds {
		^super.seconds;
	}

	seconds_ { | newSeconds|
		"Note: SCLorkClock ignores manual setting of the seconds parameter.".postln;
	}
}
