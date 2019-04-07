SCLOrkClockMonitor {
	classvar <instance = nil;

	var window;
	var clockScrollView;
	var clockUpdateTask;

	var clockViewMap;

	*new { | serverName = "sclork-s01.local" |
		SCLOrkClock.startSync(serverName);

		if (instance.isNil, {
			instance = super.new.init;
		});
		^instance;
	}

	init {
		this.prConstructUIElements;

		clockViewMap = Dictionary.new;
		clockUpdateTask = SkipJack.new({
			if (clockViewMap.size != SCLOrkClock.clockMap.size, {
				var clocksRemoved = clockViewMap.keys - SCLOrkClock.clockMap.keys;

				var clocksAdded = SCLOrkClock.clockMap.keys - clockViewMap.keys;
				clocksAdded.do({ | item, index |
					var clock = SCLOrkClock.new(item);
					clockViewMap.put(clock.name, SCLOrkClockView.new(
						clockScrollView.canvas, clock));
				});

				clocksRemoved.do({ | item, index |
					var clockView = clockViewMap.at(item);
					clockView.remove;
					clockViewMap.remove(item);
				});
			});
		});

		window.front;
	}

	prConstructUIElements {
		var scrollCanvas;

		window = SCLOrkWindow.new("SCLOrkClock Monitor",
			Rect.new(0, 0, 400, 500)
		);
		window.alwaysOnTop = true;
		window.layout = VLayout.new(
			clockScrollView = ScrollView.new
		);

		scrollCanvas = View();
		scrollCanvas.layout = VLayout();
		clockScrollView.canvas = scrollCanvas;
	}
}