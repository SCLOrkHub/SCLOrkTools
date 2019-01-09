SCLOrkClock : TempoClock {
	const beatsUpdateInterval = 0.2;
	var timeClient;
	var beatsSyncTask;
	var isPermanent;
	var shouldQuit;

	*new { | timeClient, tempo, beats, seconds, queueSize = 256 |
		var newClock = super.new(tempo, beats, seconds, queueSize);
		^newClock.prInit(timeClient);
	}

	prInit { | tC |
		timeClient = tC;
		this.beats_((Main.elapsedTime + timeClient.timeDiff) / this.tempo);

		this.permanent_(true);
		shouldQuit = false;
		CmdPeriod.add(this);

		beatsSyncTask = SkipJack.new({
			this.beats_((Main.elapsedTime + timeClient.timeDiff) / this.tempo);
		},
		dt: beatsUpdateInterval,
		stopTest: { shouldQuit }
		);
	}

	cmdPeriod {
		if (isPermanent.not, {
			shouldQuit = true;
			beatsSyncTask.stop;
		});
	}

	free {
		CmdPeriod.remove(this);
		shouldQuit = true;
		beatsSyncTask.stop;
		super.free;
	}

	permanent_ { | value |
		isPermanent = value;
		super.permanent_(value);
	}
}
