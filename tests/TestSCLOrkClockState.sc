TestSCLOrkClockState : UnitTest {

	test_secs2beats_unity {
		var state = SCLOrkClockState.new(applyAtTime: 0.0);
		this.assertEquals(state.secs2beats(0.0), 0.0);
		10.do({ | i |
			this.assertEquals(state.secs2beats(i.asFloat), i.asFloat);
		});

		// Should work for real values too.
		this.assertEquals(state.secs2beats(pi), pi);
	}

	test_secs2beats_tempos {
		var slowState = SCLOrkClockState.new(
			applyAtTime: 0.0,
			tempo: 0.5);
		var fastState = SCLOrkClockState.new(
			applyAtTime: 0.0,
			tempo: 2.0);
		10.do({ | i |
			this.assertEquals(slowState.secs2beats(i.asFloat),
				i.asFloat / 2.0);
			this.assertEquals(fastState.secs2beats(i.asFloat),
				i.asFloat * 2.0);
		});

		this.assertEquals(slowState.secs2beats(pi), pi / 2.0);
		this.assertEquals(fastState.secs2beats(pi), 2.0 * pi);
	}

	test_secs2beats_time_offset {
		var state = SCLOrkClockState.new(
			applyAtBeat: 50.0,
			applyAtTime: 100.0,
			tempo: 3.0);
		10.do({ | i |
			this.assertEquals(state.secs2beats(110.0 + i.asFloat),
				50.0 + ((10.0 + i.asFloat) * 3.0));
		});
	}

	test_secs2beats_time_server_ahead {
		var timeDiff = -500.0;
		var state = SCLOrkClockState.new(
			applyAtBeat: 3.0,
			applyAtTime: 2000.0,
			tempo: 0.25);
		// Local time of 1500.0 means server time of 2000.0, or the last
		// state change time of the clock.
		this.assertEquals(state.secs2beats(1500.0, timeDiff), 3.0);
		// Local time of 1900.0 means server time of 2400.0, or 400.0 seconds
		// in to clock running at 0.25 beats per second.
		this.assertEquals(state.secs2beats(1900.0, timeDiff), 103.0);
	}

	test_secs2beats_time_server_ahead_negative_start_time {
		var timeDiff = -3000.0;
		var state = SCLOrkClockState.new(
			applyAtBeat: 301.0,
			applyAtTime: -100.0,
			tempo: 1.25);
		// Local time of -3100.0 means server time of -100.0, or the last
		// state change time of the clock.
		this.assertEquals(state.secs2beats(-3100.0, timeDiff), 301.0);
		// Local time of -3000.0 means server time of 0.0, or 100.0 seconds
		// in to clock running at 1.25 beats per second.
		this.assertEquals(state.secs2beats(-3000.0, timeDiff), 426.0);
		// Local time of 4900.0 means server time of 7900.0, or 8000.0 seconds
		// in to clock running at 1.25 beats per second, for 10000.0 beats.
		this.assertEquals(state.secs2beats(4900.0, timeDiff), 10301.0);
	}

	test_secs2beats_time_server_behind {
		var timeDiff = 200.0;
		var state = SCLOrkClockState.new(
			applyAtBeat: 47.0,
			applyAtTime: 100.0,
			tempo: 1.5);
		// Local time of 300.0 means server time of 100.0, or the last
		// state change time of the clock.
		this.assertEquals(state.secs2beats(300.0, timeDiff), 47.0);
		// Local time of 700.0 means server time of 500.0, or 400.0 seconds
		// in to clock running at 1.5 beats per second.
		this.assertEquals(state.secs2beats(700.0, timeDiff), 647.0);
	}

	test_secs2beats_time_server_behind_negative_start_time {
		var timeDiff = 2000.0;
		var state = SCLOrkClockState.new(
			applyAtBeat: 100.0,
			applyAtTime: -1500.0,
			tempo: 1.0 / 8.0);
		// Local time of 500.0 means server time of -1500.0, or the last
		// state change time of the clock.
		this.assertEquals(state.secs2beats(500.0, timeDiff), 100.0);
		// Local time of 1300.0 means server time of -700.0, or 800.0 seconds
		// in to clock running at 1/8 of a beat per second.
		this.assertEquals(state.secs2beats(1300.0, timeDiff), 200.0);
		// Local time of 2100.0 means server time of 100.0, or 1600.0 seconds
		// in to clock running at 1/8 of a beat per second.
		this.assertEquals(state.secs2beats(2100.0, timeDiff), 300.0);
	}

	test_beats2secs_unity {
		var state = SCLOrkClockState.new(applyAtTime: 0.0);
		this.assertEquals(state.beats2secs(0.0), 0.0);
		10.do({ | i |
			this.assertEquals(state.beats2secs(i.asFloat), i.asFloat);
		});

		// Should work for real values too.
		this.assertEquals(state.beats2secs(pi), pi);
	}

	test_beats2secs_tempos {
		var slowState = SCLOrkClockState.new(
			applyAtTime: 0.0,
			tempo: 0.25);
		var fastState = SCLOrkClockState.new(
			applyAtTime: 0.0,
			tempo: 7.0);
		10.do({ | i |
			this.assertEquals(slowState.beats2secs(i.asFloat),
				i.asFloat * 4.0);
			this.assertEquals(fastState.beats2secs(i.asFloat),
				i.asFloat / 7.0);
		});

		this.assertEquals(slowState.beats2secs(pi), pi * 4.0);
		this.assertEquals(fastState.beats2secs(pi), pi / 7.0);
	}

	test_beats2secs_time_offset {
		var state = SCLOrkClockState.new(
			applyAtBeat: 9.0,
			applyAtTime: 1000.0,
			tempo: 1.0 / 5.0);
		10.do({ | i |
			this.assertEquals(state.beats2secs(9.0 + i.asFloat),
				1000.0 + (i.asFloat * 5.0));
		});
	}

	test_beats2secs_time_server_ahead {
		var timeDiff = -100.0;
		var state = SCLOrkClockState.new(
			applyAtBeat: 17.0,
			applyAtTime: 400.0,
			tempo: 0.5);
		// Beat at 17.0 means server time of 400.0, or 300.0 local time.
		this.assertEquals(state.beats2secs(17.0, timeDiff), 300.0);
		// Beat at 117.0 means server time of 600.0, or 500.0 local time.
		this.assertEquals(state.beats2secs(117.0, timeDiff), 500.0);
	}

	test_beats2secs_time_server_ahead_negative_start_time {
		var timeDiff = -1000.0;
		var state = SCLOrkClockState.new(
			applyAtBeat: 1487.0,
			applyAtTime: -4400.0,
			tempo: 16.0);
		// Beat at 1487.0 means server time of -4400.0, or -5400.0 local time.
		this.assertEquals(state.beats2secs(1487.0, timeDiff), -5400.0);
		// Beat at 161487 means server time of 5600.0, or 4600.0 local time.
		this.assertEquals(state.beats2secs(161487.0, timeDiff), 4600.0);
	}

	test_beats2secs_time_server_behind {
		var timeDiff = 300.0;
		var state = SCLOrkClockState.new(
			applyAtBeat: 23.0,
			applyAtTime: 42.0,
			tempo: 0.5);
		// Beat at 23.0 means server time of 42.0, or 342.0 local time.
		this.assertEquals(state.beats2secs(23.0, timeDiff), 342.0);
		// Beat at 123.0 means server time of 242.0, or 542.0 local time.
		this.assertEquals(state.beats2secs(123.0, timeDiff), 542.0);
	}

	test_beats2secs_time_server_behind_negative_start_time {
		var timeDiff = 7000.0;
		var state = SCLOrkClockState.new(
			applyAtBeat: 666.0,
			applyAtTime: -200.0,
			tempo: 2.0);
		// Beat at 666.0 means server time of -200.0, or 6800.0 local time.
		this.assertEquals(state.beats2secs(666.0, timeDiff), 6800.0);
		// Beat at 1466.0 means server time of 200.0, or 7200.0 local time.
		this.assertEquals(state.beats2secs(1466.0, timeDiff), 7200.0);
	}
}