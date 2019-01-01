SCLOrkClock : TempoClock {
	const beatsUpdateInterval = 0.2;
	var timeClient;
	var beatsSyncTask;

	*new { | timeClient, tempo, beats, seconds, queueSize = 256 |
		var newClock = super.new(tempo, beats, seconds, queueSize);
		^newClock.prInit(timeClient);
	}

	prInit { | tC |
		timeClient = tC;
		this.beats_((Main.elapsedTime + timeClient.timeDiff) / this.tempo);

		beatsSyncTask = Task.new({
			while ({ true }, {
				beatsUpdateInterval.wait;
				this.beats_((Main.elapsedTime + timeClient.timeDiff) / this.tempo);
			});
		}, SystemClock).start;
	}
}
