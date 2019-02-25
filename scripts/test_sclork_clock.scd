(
~server = SCLOrkClockServer.new;
SCLOrkClock.startSync("127.0.0.1");
)

(
~clock = SCLOrkClock.new;
~clock.play({ | beats, seconds, clock |
	"%: beats: %, seconds: %, tempo: %, timeDiff: %".format(
		clock.name, beats, seconds, clock.tempo, SCLOrkClock.timeDiff).postln;
	1;
});
)

(
~clock.setTempoAtBeat(3.0, 16.0);
~clock.setTempoAtBeat(0.5, 32.0);
~clock.setTempoAtBeat(1.0, 24.0);
)

(
~clock.tempo = 2.0;
)

(
~clock2 = SCLOrkClock.new(\luke, 0.5);
~clock2.play({ | beats, seconds, clock |
	"%: beats: %, seconds: %, tempo: %".format(
		clock.name, beats, seconds, clock.tempo).postln;
	1;
});
~clock2.permanent = true;
)

(
~clock3 = SCLOrkClock.new;
~clock.play({ | beats, seconds, clock |
	"%: beats: %, seconds: %, tempo: %".format(
		clock.name, beats, seconds, clock.tempo).postln;
	1;
});
)

(
TestSCLOrkClockState.run;
)
