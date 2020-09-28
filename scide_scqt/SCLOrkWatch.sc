/*
Usage:
SCLOrkWatch.new;

Optionally, user may define a list of target times to get a blink warning.
Blinking will start 5 seconds before target:

SCLOrkWatch.new(["0:13", "0:30", "01:11"]);

Optionally, user can enter list in the format "target times & actions":

[
"0:05", { "wow".postln },
"0:10", { "boo".posltn },
]

The function will be evaluated at the corresponding time.

*/

SCLOrkWatch {

	var <win;
	var secondsView, minutesView, separatorView;
	var winWidth = 400, winHeight = 160;
	var timeInSeconds = 0;
	var mm = 0, ss = 0, updateMinSec, to_seconds;
	var formatString;
	var font, t;
	var blinkFunction, blinkTimer, blinkDuration = 5;
	var <>blinkList;
	var blinkEndAction;

	*new { arg list; ^super.new.init(list); }

	init { arg list;

		// blinkList stores *blink start times*, not target times
		// blink start time is (target - blinkDuration)

		// new blink list array
		blinkList = {
			var min, sec, target, start;
			list.collect({ arg item;
				if(item.isString, {
					min = item.split($:).at(0).asInteger;
					sec = item.split($:).at(1).asInteger;
					target = min * 60 + sec;
					start = target - 5;
					start;
				}, {
					if(item.isFunction, { item })
				});
			});
		}.value;

		blinkList.postln;

		font = Font("Courier", 130);
		t = TempoClock.new(1).permanent_(true);
		blinkTimer = blinkDuration;

		win = Window.new(
			name: "SCLOrkWatch",
			bounds: Rect(0, 0, winWidth, winHeight),
			resizable: false
		).front.alwaysOnTop_(true);

		minutesView = StaticText.new(win);
		separatorView = StaticText.new(win);
		secondsView = StaticText.new(win);

		win.layout = HLayout(
			// Button.new(win),
			// Button.new(win),
			// Button.new(win),
			[minutesView, stretch: 1],
			[separatorView, stretch: 0],
			[secondsView, stretch: 1]

		);

		minutesView.align = \right;
		separatorView.align = \center;
		secondsView.align = \left;

		secondsView.font = font;
		separatorView.font = Font("Courier", 90);
		minutesView.font = font;

		// add padding 0 if number is below 10
		formatString = { arg n;
			if(n<10,
				{ "0" ++ n.asString },
				{ n.asString }
			)
		};

		minutesView.string = formatString.value(mm);
		separatorView.string = ":";
		secondsView.string = formatString.value(ss);

		updateMinSec = {
			mm = (timeInSeconds / 60).floor.asInteger;
			ss = (timeInSeconds % 60).asInteger;
		};

		blinkFunction = {
			{
				while( { blinkTimer > 0 },
					{
						{
							minutesView.stringColor = Color.red;
							secondsView.stringColor = Color.red;
						}.defer;

						0.5.wait;

						{
							minutesView.stringColor = Color.black;
							secondsView.stringColor = Color.black;
						}.defer;

						0.5.wait;

						// decrement timer (like a real timer)
						blinkTimer = blinkTimer - 1;
					}
				);

				{
					minutesView.background = Color.green;
					secondsView.background = Color.green;
				}.defer;

				// do Action at end of blink (on green):
				if( blinkEndAction.isFunction, blinkEndAction, {"no action".postln});

				0.5.wait;

				{
					minutesView.background = Color.green(1, 0);
					secondsView.background = Color.green(1, 0);
				}.defer;

				// reset timer
				blinkTimer = blinkDuration;


			}.fork(t);
		};

		// SkipJack is a utility to run a function in the background repeatedly;
		// Function survives cmd-period.
		SkipJack.new(
			updateFunc: {
				// get abs number of seconds now
				timeInSeconds = t.beats.round(1.0).asInteger;

				// convert to mm:ss and update variables mm and ss
				updateMinSec.value(timeInSeconds);

				// update GUI display
				{ minutesView.string = formatString.value(mm) }.defer;
				{ secondsView.string = formatString.value(ss) }.defer;

				// blink if needed
				// note: blinkList stores *blink start times*, not target times
				{
					if( blinkList.notNil, {
						var index = blinkList.indexOf(timeInSeconds);
						if(index.notNil, {
							// start blinking
							blinkFunction.value;
							// save corresponding action (if any)
							if( blinkList.at(index+1).isFunction, {
								blinkEndAction = blinkList.at(index + 1);
							}, {
								blinkEndAction = nil
							});
						})
					})
				}.defer;
			},
			dt: 1,
			stopTest: { win.isClosed },
			name: "SCLOrkWatch",
			clock: t
		);

		win.onClose = {
			("SCLOrkWatch has been closed at " ++ formatString.value(mm) ++ ":" ++ formatString.value(ss)).postln;
			SkipJack.stop("SCLOrkWatch");

		};

	} // end of init
} // end of class
