
/*
SCLOrkQuNeo
by Tanya Sonker and Bruno Ruviaro, 2019

Graphical User Interface mirroring the QuNeo MIDI controller. It can be used as a standalone GUI in SuperCollider, or in conjunction with an actual QuNeo hardware. When used with the hardware, GUI and QuNeo mirror each other (ie, pushing a button on the device will be reflected on the GUI and vice-versa).

User can create assign custom functions to any button or slider in the following way:

z = SCLOrkQuNeo.new;
z.onButtonChange = { |velocity, midinote| ["BUTTON!", velocity, midinote].postln };
z.onSliderChange = { |ccValue, ccNum| ["SLIDING...", ccValue, ccNum].postln };

GUI design by Tanya Sonker, Summer 2019.
MIDI functionality and class by Bruno Ruviaro, Fall 2019.
Thanks to Luke Nihlen for giving pointers and example code for writing it as a SC Class.

GUI design:

These variables hold CompositeViews placed onto main window:
___________________________
|  leftTop   |              |
|____________|              |
|            |              |
| leftBottom |     right    |
|            |              |
|____________|              |
|            |              |
|    vader   |              |
|            |______________|
|            |   footer     |
|____________|______________|

*/

SCLOrkQuNeo {
	var buttonArray, buttonNamesAsMidinotes;
	var sliderArray, sliderLabelArray;
	var window, windowWidth, windowHeight; // main window
	var leftTop, leftTopW, leftTopH; // top left 4-button row
	var leftBottom, leftBottomW, leftBottomH; // buttons and Hsliders above vader
	var right, rightW, rightH; // 16-button main panel
	var vader, vaderW, vaderH; // darth vader (lower left box)
	var footer, footerW, footerH; // buttons and long slider below 16-button panel
	var nose;
	var guiPreset;
	var bank;
	var toQuNeo;
	var midiIsOn;
	var makeSlider, makeKnob;
	var makeSliderLabel;
	var leftBottomSliderLabels;
	var vaderSliderLabels;
	var footerSliderLabel;

	var <>onButtonChange;
	var <>onSliderChange;
	var <>midiChannel = 11; // SCLOrk QuNeos are always channel 11


	*new { |action| ^super.new.init(action); }


	init { |argAction|

		// start GUI in \normal preset, bank 0
		guiPreset = \normal; // options are \normal and \toggle
		bank = 0;

		// MIDI is off by default
		midiIsOn = false;

		// create empty button array to hold all Buttons
		// (each button stored at index corresponding to its midinote number)
		buttonArray = Array.newClear(127);

		// create empty array to hold all sliders
		// (each slider stored at index corresponding to its midi cc number)
		sliderArray = Array.newClear(127);

		// create empty array to hold StaticText objects sitting underneath sliders which will be their labels
		sliderLabelArray = Array.newClear(127);

		// create main window
		windowWidth = 840;
		windowHeight = 600;

		// leftmost column occupies 1/3 of window width
		leftTopW = windowWidth * 1/3;
		leftBottomW = windowWidth * 1/3;
		vaderW = windowWidth * 1/3;

		// leftmost column is split vertically this way among its parts
		leftTopH = windowHeight * 1/10;
		leftBottomH = windowHeight * 4/10;
		vaderH = windowHeight * 5/10;

		// right column occupies the rest of window width
		rightW = windowWidth * 2/3;
		footerW = windowWidth * 2/3;

		// right column vertical spacing
		rightH = windowHeight * 9/10;
		footerH = windowHeight * 1/10;

		window = Window.new(name: "SCLOrkQuNeo", bounds: Rect(
			left: 275,
			top: 163,
			width: windowWidth,
			height: windowHeight
		),
		resizable: false
		).front;

		window.background = Color.black;

		window.onClose = {
			"Closed GUI, freeing all MIDIdefs".postln;
			MIDIdef.freeAll;
			// add code to turn off all LEDs
		};

		makeSliderLabel = { | parent, ccNum |
			var slider = sliderArray[ccNum];
			var composite = CompositeView.new(parent, slider.bounds);
			sliderLabelArray[ccNum] = StaticText.new(
				parent: composite,
				bounds: Rect(0, 0, composite.bounds.width, composite.bounds.height))
			.string_("cc"++ccNum.asString)
			.stringColor_(Color.black)
			.background_(Color.white)
			.align_(\center);

		};

		makeSlider = { | parent, bounds, ccNum |
			Slider.new(parent, bounds).action_({ |slider|
				// Hook to user-customazible slider function (note that slider value is between 0 and 1, not 127)
				this.onUISliderChange(slider.value, ccNum);
				// Send LEDs back to QuNeo if applicable
				if(midiIsOn, {
					this.prSendSliderLED(ccNum, slider.value)
				});

			}).background_(Color.gray(1, 0));
		};

		makeKnob = { | parent, ccNum |
			Knob(parent).minHeight_(105)
			.action = { |knob|
				// Hook to user-customazible knob functions
				// Works just like a slider, so we use that method
				this.onUISliderChange(knob.value, ccNum);
				// Send LEDs back to QuNeo if applicable
				if(midiIsOn, {
					this.prSendKnobLED(ccNum, knob.value)
				});
			};
		};

		// ================
		// *** leftTop ***
		// ================

		leftTop = CompositeView(
			parent: window,
			bounds: Rect(
				left: 0,
				top: 5,
				width: leftTopW,
				height: leftTopH
			)
		);


		// Button 125 turns MIDI on and off.
		// Button 125 will *not* respond to MIDI -- it's a GUI-only option.
		buttonArray[125] = Button.new()
		.states_([
			["MIDI OFF", Color.black, Color.white],
			["MIDI ON", Color.white, Color.blue]
		])
		.action_({ |b|
			if(b.value==1, {
				{
					"Waiting for MIDIClient to initialize...".postln;
					MIDIClient.init;
					3.wait;
					"Almost there...".postln;
					1.wait;
					if(MIDIClient.initialized, {
						this.connectQuNeo;
						this.onConnectMIDI(true);
					}, {
						"Failed to initialize MIDI".postln;
					});
				}.fork;
			}, {
				"midi off".postln;
				this.onConnectMIDI(false)

			});
		})
		.minWidth_(65);

		// Button 126 allows user to switch GUI from Preset Normal to Preset Toggle
		// Button 126 will *not* respond to MIDI -- it's a GUI-only option.
		buttonArray[126] = Button.new()
		.states_([
			["Normal", Color.black, Color.white],
			["Toggle", Color.white, Color.black]
		])
		.action_({ |b|
			guiPreset = if(b.value==0, {\normal}, {\toggle}); // switch between presets
			["Current preset", guiPreset].postln;
		})
		.minWidth_(65);

		// Create three other topLeft buttons: QuNeo's diamond, square, triangle
		// Button numbers correspond to QuNeo preset midinote numbers.
		// Buttons are stored at array slot corresponding to midinote number
		// These buttons only have velocity 0 and 127, nothing in between
		[24, 25, 26].do({ |midinote|
			var downColor =
			switch( midinote,
				24, {Color.red},
				25, {Color.yellow},
				26, {Color.green}
			);
			buttonArray[midinote] = Button.new()
			.states_([
				[midinote, Color.black, Color.white],
				[midinote, Color.black, downColor] // color when pushed in and held
			])
			.mouseDownAction_({
				buttonArray[midinote].valueAction = 1;
				this.onUIButtonChange(
					velocity: 127,
					midinote: midinote
				);
			})
			.action_({ |button|
				// "note off" action
				if(button.value==0, {
					this.onUIButtonChange(
						velocity: 0,
						midinote: midinote
				)});
				// Send LEDs back to QuNeo if applicable
				if(midiIsOn, {
					if(buttonArray[midinote].value==0, {
						this.prSendOtherLEDs(midinote, 0)
					}, {
						this.prSendOtherLEDs(midinote, 127)
					})
				});
			})
			.minHeight_(42)
		});

		// Place buttons into leftTop layout
		leftTop.layout = HLayout(
			10, // empty space
			VLayout(
				[buttonArray[125], align: \bottom],
				[buttonArray[126], align: \top]
			),
			15,
			[buttonArray[24], align: \bottom],
			[buttonArray[25], align: \bottom],
			[buttonArray[26], align: \bottom],
			20
		);


		// ==================
		// *** leftBottom ***
		// ==================


		leftBottomSliderLabels = CompositeView(
			parent: window,
			bounds: Rect(
				left: 0,
				top: leftTopH,
				width: leftBottomW,
				height: leftBottomH
			)
		);

		leftBottom = CompositeView(
			parent: window,
			bounds: Rect(
				left: 0,
				top: leftTopH,
				width: leftBottomW,
				height: leftBottomH
			)
		);

		leftBottom.decorator = FlowLayout(
			bounds: leftBottom.bounds,
			margin: 18@18,
			gap: 7@20
		);


		// Create leftBottom buttons and sliders, store them into proper arrays & indices
		[
			[11, 12, 0], // button #, button #, horizontal slider CC#
			[13, 14, 1],
			[15, 16, 2],
			[17, 18, 3]
		].do({ |row|
			// Two buttons...
			2.do({ |i|
				var midinote = row[i];
				buttonArray[midinote] = Button(parent: leftBottom, bounds: 25@35)
				.states_([
					[midinote, Color.black, Color.white],
					[midinote, Color.white, Color.black]
				])
				.mouseDownAction_({
					buttonArray[midinote].valueAction = 1;
					this.onUIButtonChange(
						velocity: 127,
						midinote: midinote
					);
				})
				.action_({ |button|
					// "note off" action
					if(button.value==0, {
						this.onUIButtonChange(
							velocity: 0,
							midinote: midinote
					)});
					// Send LEDs back to QuNeo if applicable
					if(midiIsOn, {
						if(buttonArray[midinote].value==0, {
							this.prSendOtherLEDs(midinote, 0)
						}, {
							this.prSendOtherLEDs(midinote, 127)
						})
					});
				})
			});
			// ... followed by a horizontal slider
			sliderArray[row[2]] = makeSlider.value(parent: leftBottom, bounds: 170@35, ccNum: row[2]);
			makeSliderLabel.value(leftBottomSliderLabels, row[2]);
			// leftBottomSlider.front;
		});

		// =============
		// *** right ***
		// (16-button box)
		// 4 layers x 16 buttons
		// =============

		// Adding buttons through FlowLayout number buttons from left to right, top to bottom.
		// However, we want to follow the midinote mapping scheme of SCLOrk QuNeo presets,
		// which start from midinote 36 at lower left, then go left to right and bottom up.
		// This array is used for that purpose.
		buttonNamesAsMidinotes = [
			48, 49, 50, 51,
			44, 45, 46, 47,
			40, 41, 42, 43,
			36, 37, 38, 39
		];

		// Create array with 4 CompositeViews layered on top of each other
		// Each one will hold 16 buttons
		// right[0] -> midinotes 36 to 51
		// right[1] -> midinotes 52 to 67
		// right[2] -> midinotes 68 to 83
		// right[3] -> midinotes 84 to 89
		right = Array.fill(4, {
			CompositeView(
				parent: window,
				bounds: Rect(
					left: leftTopW,
					top: 0,
					width: rightW,
					height: rightH;
				)
			);
		});

		right.do({ |cv| cv.decorator = FlowLayout(
			bounds: cv.bounds,
			margin: 12@12,
			gap: 15@15
		);
		});

		// Populate buttonArray with 16x4 buttons
		// Buttons get assigned into array slots corresponding to their midinote number
		Array.fill(16*4, { |i|
			var index = i.mod(16);
			var layer = i.div(16);
			var midinote;
			// use midinote numbers as button number:
			midinote = buttonNamesAsMidinotes[index] + (16 * layer);
			buttonArray[midinote] = Button.new(right[layer], 120@120)
			.states_([
				[midinote, Color.black, Color.white],
				[midinote, Color.white, Color.black]
			])
			.mouseDownAction_({ |velocity|
				// mouseDownAction only runs if we are in Normal mode, not Toggle mode.
				if(guiPreset==\normal, {
					velocity = if(velocity.isNumber, {velocity}, {127});
					buttonArray[midinote].value = 1; // display button as active
					buttonArray[midinote].action.value(velocity); // run action right away, pass velocity
				});
			})
			.action_({ |velocity|
				// is incoming arg a number? (if not, it's a Button)
				velocity = if(velocity.isNumber, {velocity}, {velocity.value * 127});
				this.onUIButtonChange(velocity, midinote);

				// Send LED commands to QuNeo if applicable:
				if(midiIsOn, {
					if(buttonArray[midinote].value==0, {
						this.prSendPadLEDs(midinote, \off)
					}, {
						this.prSendPadLEDs(midinote, \red)
					})
				})
			})
		});

		/* ^^^^ mouseDownAction and action functions above may be called from either a regular GUI button press, or from a MIDIdef listening to QuNeo buttons. When called from the MIDIdef, QuNeo velocity is passed as arg. When called by regular GUI button press, the button itself is passed as arg, in which case we ignore it and assign a default velocity of 127. */

		// first layer is on top at start
		right[0].front;


		// =============
		// *** vader ***
		// =============

		vaderSliderLabels = CompositeView(
			parent: window,
			bounds: Rect(
				left: 0,
				top: leftTopH + leftBottomH,
				width: vaderW,
				height: vaderH
			)
		);

		vader = CompositeView(
			parent: window,
			bounds: Rect(
				left: 0,
				top: leftTopH + leftBottomH,
				width: vaderW,
				height: vaderH
			)
		);

		// create Knobs, place it in sliderArray (makes sense here, they are just like sliders)
		sliderArray[4] = makeKnob.value(parent: vader, ccNum: 4);
		sliderArray[5] = makeKnob.value(parent: vader, ccNum: 5);

		// nose is hard coded in the right spot
		nose = Button(parent: vader, bounds: Rect(vader.bounds.width / 2 - 15, 95, 30, 30));
		nose.states = [
			["", Color.gray, Color.white],
			["", Color.gray, Color.green],
			["", Color.gray, Color.new(1, 0.6)], // orange
			["", Color.gray, Color.red],
		];

		// nose action to change 16-button layer (switch banks):
		nose.action = { |button|
			bank = button.value;
			right[bank].front;

			// toggle preset housekeeping: turn off previous bank LEDs, and redraw for new bank (in case any pads were on from a previous visit to that bank)
			if( (guiPreset==\toggle) && (midiIsOn), {
				this.prRedrawPadLEDsAtBankSwitch;
			});

			// normal preset housekeeping: if QuNeo player keeps any pads down while switching bank, those pads will not get a proper note off message. The command below makes sure all buttons are turned off when switching banks.
			if(guiPreset==\normal, {
				buttonArray[36..99].do({ |b| b.valueAction = 0 });
			});

			// send LED info back to QuNeo Rhombus button:
			if(midiIsOn, {
				switch(bank,
					0, { // LEDs off
						toQuNeo.noteOn(0, 44, 0);
						toQuNeo.noteOn(0, 45, 0);
					},
					1, { // LEDs green
						toQuNeo.noteOn(0, 44, 127);
						toQuNeo.noteOn(0, 45, 0);
					},
					2, { // LEDs orange
						toQuNeo.noteOn(0, 44, 127);
						toQuNeo.noteOn(0, 45, 127);
					},
					3, { // LEDs red
						toQuNeo.noteOn(0, 44, 0);
						toQuNeo.noteOn(0, 45, 127);
					}
				);
			})
		};

		sliderArray[6] = makeSlider.value(parent: vader, ccNum: 6);
		sliderArray[7] = makeSlider.value(parent: vader, ccNum: 7);
		sliderArray[8] = makeSlider.value(parent: vader, ccNum: 8);
		sliderArray[9] = makeSlider.value(parent: vader, ccNum: 9);

		vader.layout = VLayout(
			HLayout(
				sliderArray[4], sliderArray[5]

			),
			10, // empty space
			HLayout(
				10,
				sliderArray[6],
				10,
				sliderArray[7],
				10,
				sliderArray[8],
				10,
				sliderArray[9],
				10
			)
		);

		[6, 7, 8, 9].do({ |i|
			makeSliderLabel.value(vaderSliderLabels, i);
		});




		// ===============
		// *** footer ***
		// ===============

		footerSliderLabel = CompositeView(
			parent: window,
			bounds: Rect(
				left: vaderW,
				top: rightH,
				width: footerW,
				height: footerH
			)
		);

		footer = CompositeView(
			parent: window,
			bounds: Rect(
				left: vaderW,
				top: rightH,
				width: footerW,
				height: footerH
			)
		);

		[20, 21, 22, 23].do({ |midinote|
			buttonArray[midinote] = Button.new(parent: footer)
			.states_([
				[midinote, Color.black, Color.white],
				[midinote, Color.white, Color.black]
			])
			.mouseDownAction_({
				buttonArray[midinote].valueAction = 1;
				this.onUIButtonChange(
					velocity: 127,
					midinote: midinote
				);
			})
			.action_({ |button|
				// "note off" action
				if(button.value==0, {
					this.onUIButtonChange(
						velocity: 0,
						midinote: midinote
				)});
				// Send LEDs back to QuNeo if applicable
				if(midiIsOn, {
					if(buttonArray[midinote].value==0, {
						this.prSendOtherLEDs(midinote, 0)
					}, {
						this.prSendOtherLEDs(midinote, 127)
					})
				});
			})
			.maxWidth_(55)
		});

		sliderArray[10] = makeSlider.value(parent: footer, ccNum: 10).orientation_(\horizontal);

		footer.layout = HLayout(
			2,
			// footer left buttons
			VLayout(
				buttonArray[20],
				buttonArray[21]
			),
			// footer long slider
			[sliderArray[10], stretch: 1],
			// footer right buttons
			VLayout(
				buttonArray[22],
				buttonArray[23]
			),
			15
		);

		makeSliderLabel.value(footerSliderLabel, 10);

	} // end of init



	onUIButtonChange { | velocity, midinote |
		this.onButtonChange.value(velocity, midinote)
	}

	onUISliderChange { | ccValue, ccNum |
		this.onSliderChange.value(ccValue, ccNum)
	}

	// Customize label on pad buttons. Strings that are too long will not fit nicely on button. One or two short words are best. You can force a line break in the string using \n if needed.
	// examples for help file:
	/*
	(
	z.changeButtonLabel(midinote: 36, string: "POPCORN \n WOW");
	z.changeButtonLabel(midinote: 37, string: "REVERB");
	z.changeButtonLabel(midinote: 45, string: "A LOT OF \n DELAY", keepNumber: false);
	z.changeButtonLabel(midinote: 56, string: "echoes \n galore...");
	z.changeButtonLabel(midinote: 67, string: "A BIT OF \n DELAY", keepNumber: false);
	)
	*/

	changeButtonLabel { | midinote, string, keepNumber = true |

		var newLabel;

		if(keepNumber, {
			newLabel = midinote.asString ++ ". " ++ string
		}, {
			newLabel = string;
		});

		buttonArray[midinote].states_([
			[newLabel, Color.black, Color.white],
			[newLabel, Color.white, Color.black]
		])
	}


	/*
	// Customize slider labels. You can force a line break in the string using \n if needed. Set keepNumber to false to remove the default MIDI cc number label.

	For help file, examples on how to change slider labels:

	z.changeSliderLabel(ccNum: 0, string: "tomatos");
	z.changeSliderLabel(ccNum: 1, string: "carrots");
	z.changeSliderLabel(ccNum: 2, string: "onions");
	z.changeSliderLabel(ccNum: 3, string: "garlic");

	z.changeSliderLabel(ccNum: 6, string: "\nwow\nmany\nlines");
	z.changeSliderLabel(ccNum: 7, string: "\noops");
	z.changeSliderLabel(ccNum: 8, string: "hi", keepNumber: false);
	z.changeSliderLabel(ccNum: 9, string: "bye", keepNumber: false);

	z.changeSliderLabel(ccNum: 10, string: "maybe this is master volume", keepNumber: false);

	*/
	changeSliderLabel { | ccNum, string, keepNumber = true |

		var newLabel;

		if(keepNumber, {
			newLabel = "cc" ++ (ccNum.asString) ++ " " ++ string
		}, {
			newLabel = string;
		});

		sliderLabelArray[ccNum].string = newLabel;
	}



	onConnectMIDI { | doConnect |
		if (doConnect, {
			var midiPort;

			// set MIDI flag to true:
			midiIsOn = true;

			// create MIDIOut to send LED command back to QuNeo
			toQuNeo = MIDIOut(0).latency_(0);

			midiPort = MIDIIn.findPort("QUNEO", "QUNEO MIDI 1");
			// .notNil or: MIDIIn.findPort("QUNEO", "QUNEO").notNil;

			if (midiPort.notNil, {

				"QuNeo found:".postln;
				midiPort.postln;
				"Make sure you are in QuNeo preset #3:".postln;
				"1. Push small round button on top left corner of QuNeo".postln;
				"2. Push pad #3 to select the third preset".postln;

				MIDIdef.cc(
					key: \sliders,
					func: { | ccValue, ccNumber |
						var sliderValue, knobValue;
						{
							if((ccNumber<4) || (ccNumber>5), {
								// do this for regular sliders (not knobs)
								sliderValue = ccValue.linlin(0, 127, 0, 1);
								sliderArray[ccNumber].valueAction = sliderValue;
							}, {
								// ...and for the two knobs:
								// weird scaling. Ignore knob-bottom numbers (51-77) to mimic SC GUI where knob starts at 5pm and goes up to 4pm.
								if(ccValue<51, {
									knobValue = ccValue.linlin(0, 50, 0.5, 1);
									sliderArray[ccNumber].valueAction = knobValue;
								});
								if(ccValue>77, {
									knobValue = ccValue.linlin(78, 127, 0, 0.5);
									sliderArray[ccNumber].valueAction = knobValue;
								});
							});
						}.defer;
					},
					ccNum: (0..10),
					chan: midiChannel
				).permanent_(true);

				MIDIdef.noteOn(
					key: \nose,
					func: {
						bank = (bank + 1).mod(4);
						{ nose.valueAction = bank }.defer;
					},
					noteNum: 19,
					chan: midiChannel
				).permanent_(true);

				MIDIdef.noteOn(
					key: \padsOn,
					func: { | velocity, midinote |

						case
						{bank==0} {midinote}
						{bank==1} {midinote = midinote + 16}
						{bank==2} {midinote = midinote + 32}
						{bank==3} {midinote = midinote + 48}
						;

						{
							if(guiPreset==\normal, {
								buttonArray[midinote].mouseDownAction.value(velocity);
							}, { // if toggle,
								// turn on if off; turn off if on:
								buttonArray[midinote].value = 1 - buttonArray[midinote].value;
								// run action with incoming velocity if button is being turned on,
								// otherwise consider it a "note off" with velocity 0:
								if(buttonArray[midinote].value==1, {
									buttonArray[midinote].action.value(velocity);
								}, {
									buttonArray[midinote].action.value(0);
								});
							})
						}.defer;
					},
					noteNum: (36..99), // pads only
					chan: midiChannel,
					// srcID: midiPort.uid
				).permanent_(true);

				MIDIdef.noteOff(
					key: \padsOff,
					func: { | velocity, midinote |
						case
						{bank==0} {midinote}
						{bank==1} {midinote = midinote + 16}
						{bank==2} {midinote = midinote + 32}
						{bank==3} {midinote = midinote + 48}
						;
						if(guiPreset==\normal, {
							{ buttonArray[midinote].valueAction = 0 }.defer;
						});
						// don't do anything with note offs if in Toggle mode
					},
					noteNum: (36..99), // pads only
					chan: midiChannel
					// srcID: midiPort.uid
				).permanent_(true);

				MIDIdef.noteOn(
					key: \noteOnOtherButtons,
					func: { | velocity, midinote |
						{buttonArray[midinote].mouseDownAction.value(velocity)}.defer;
					},
					noteNum: (11..18)++(20..26), // all other non-pad buttons except nose
					chan: midiChannel,
					// srcID: midiPort.uid
				).permanent_(true);


				MIDIdef.noteOff(
					key: \noteOffOtherButtons,
					func: { | velocity, midinote |
						{buttonArray[midinote].valueAction = 0}.defer;
					},
					noteNum: (11..18)++(20..26),
					chan: midiChannel,
					// srcID: midiPort.uid
				).permanent_(true);



			}, {
				"Unable to find QuNeo".postln;
				this.isPhysicalDeviceConnected(verbose: true);
				{ buttonArray[125].value = 0 }.defer;
			});
		}, {
			"free all MIDIdefs".postln;
			MIDIdef.freeAll;
			midiIsOn = false;
		});
	}

	// Returns boolean: true if QuNeo is detected.
	// Testing in two ways:
	// Can Command Line see QuNeo?
	// Can SuperCollider see QuNeo?
	// MIDIIn.findPort alone is not enough as it will not know if QuNeo has been unplugged unless we reinitialized MIDIClient
	isPhysicalDeviceConnected { |verbose = false|
		var pipe = Pipe.new("amidi -l", "r");
		var line = pipe.getLine;
		var quneoCmdLine = false;
		var quneoSC = false;

		// is the Command Line seeing the device?
		while({ (line.notNil) && (quneoCmdLine.not) }, {
			quneoCmdLine = line.asString.containsi("QUNEO");
			line = pipe.getLine;
		});
		pipe.close;

		// is SuperCollider seeing the device?

		quneoSC = MIDIClient.destinations.collect({ |midiEndPoint|
			midiEndPoint.name.asString.containsi("QUNEO");
		});
		quneoSC = if(quneoSC.isNil, {false}, {if(quneoSC.includes(true), {true}, {false})});

		case
		{verbose.not} {"don't post any messages"}
		{quneoCmdLine.not} {"Hardware not detected. Is QuNeo plugged in?".postln}
		{quneoSC.not} {"SuperCollider is not seeing QuNeo. Initialize SuperCollider's MIDIClient?".postln}
		;

		^(quneoCmdLine && quneoSC);
	}
	
	preset { |mode| if(mode==\normal, {buttonArray[126].valueAction = 0}); if(mode==\toggle, {buttonArray[126].valueAction = 1}); }


	connectQuNeo {
		var pipe = Pipe.new("jack_lsp", "r");
		var line = pipe.getLine; // get the first line right away
		var qOut, qIn;
		var scOut, scIn;

		// go through all available ports (overkill, but OK for now)
		while({ line.notNil }, {
			// make sure it's a string
			line = line.asString;

			// Is this a QuNeo port? If so, save it
			if(line.containsi("QUNEO"), {
				if(line.containsi("capture"), { qOut = line });
				if(line.containsi("playback"), { qIn = line });
			});

			// Is this a SuperCollider MIDI port? If so, save it
			if(line.containsi("a2j:SuperCollider"), {
				if(line.containsi("out0"), { scOut = line });
				if(line.containsi("in0"), { scIn = line });
			});

			// get a new line before while runs again
			line = pipe.getLine;
		});
		pipe.close;
		["qOut", qOut].postln;
		["qIn", qIn].postln;
		["scOut", scOut].postln;
		["scIn", scIn].postln;

		// Make the right connections
		if( (qOut.notNil) && (qIn.notNil) && (scOut.notNil) && (scIn.notNil), {
			("jack_connect \"" ++ qOut ++ "\" \"" ++ scIn ++ "\"").unixCmd;
			("jack_connect \"" ++ qIn ++ "\" \"" ++ scOut ++ "\"").unixCmd;
		}, {
			"Some of the ports could not be found, no connections made".postln;
		})
	}

	prSendPadLEDs { |midinote, color|

		var padNumber = (midinote - 36).mod(16);

		switch(color,
			\green, {
				toQuNeo.noteOn(0, padNumber*2, 127); // greens are even numbers
				toQuNeo.noteOn(0, padNumber*2+1, 0); // reds are one above greens
			},
			\orange, {
				toQuNeo.noteOn(0, padNumber*2, 127);
				toQuNeo.noteOn(0, padNumber*2+1, 127);
			},
			\red, {
				toQuNeo.noteOn(0, padNumber*2, 0);
				toQuNeo.noteOn(0, padNumber*2+1, 127);
			},
			\off, {
				toQuNeo.noteOn(0, padNumber*2, 0);
				toQuNeo.noteOn(0, padNumber*2+1, 0);
			}
		);
	}

	prAllPadLEDsOff {
		(36..51).do({ |midinote| this.prSendPadLEDs(midinote, \off) });
	}

	prSendOtherLEDs { |number, value|

		// for all other non-pad buttons and sliders

		// REC, STOP, PLAY buttons, incoming miditnoes 24-25-26 mapped to outgoing MIDIOut notes 33-34-35, default colors
		if( (number >= 24) && (number <= 26), { toQuNeo.noteOn(0, number+9, value) });

		// LEFT / RIGHT arrow buttons (4 pairs), incoming midinotes 11-18 mapped to outgoing MIDIOut notes 36-43, default colors
		if( (number >= 11) && (number <= 18), { toQuNeo.noteOn(0, number+25, value) });

		// SKIP Rhombus (incoming midinote 19), as I previously hard coded it elsewhere because of need to mix colors. Leave as is.

		// UP / DOWN arrow buttons (2 pairs), incoming midinotes 20-23 mapped to outgoing MIDIOut notes 46-49, default colors
		if( (number >= 20) && (number <= 23), { toQuNeo.noteOn(0, number+26, value) });
	}

	prSendSliderLED { |ccNum, sliderValue|
		var sliderLEDccNumber;

		sliderLEDccNumber = switch(ccNum,
			// horizontal sliders
			0, {11},
			1, {10},
			2, {9},
			3, {8},
			// vertical sliders
			6, {1},
			7, {2},
			8, {3},
			9, {4},
			// long slider
			10, {5},
		);

		// slider value comes in between 0-1, need to convert to 0-127
		toQuNeo.control(0, sliderLEDccNumber, sliderValue.linlin(0, 1, 0, 127));
	}

	prSendKnobLED { |ccNum, knobValue|
		var knobLEDccNumber, ccValue;
		knobLEDccNumber = switch(ccNum,
			4, {6},
			5, {7}
		);
		// weird re-scaling of numbers because we want the Rotary on QuNeo to behave like the Knob on SuperCollider. The QuNeo rotary has 0 at noon position, but SC's knobs have zero at 7pm and 1 at 5pm.
		ccValue = knobValue.linlin(0, 1, 85, 183).mod(127);
		toQuNeo.control(0, knobLEDccNumber, ccValue);


	}

	// Note: for some reason the Long Slider and the two Rotaries (Darth Vader's eyes) won't turn off completely. Instead they just go to location 0. All other LEDs do turn off with this method.
	prAllLEDsOff {
		(0..126).do({ |midinote| toQuNeo.noteOn(0, midinote, 0); toQuNeo.control(0, midinote, 0) });
	}

	prRedrawPadLEDsAtBankSwitch {
		// Only needed for Toggle mode.
		// As bank switching is done manually and not in the QuNeo itself, we need to redraw active vs inactive LEDs every time the user switches banks from the QuNeo.

		// First step, turn all 16 pads LEDs off (clear up previous bank display):
		this.prAllPadLEDsOff;

		// Second step, check which midinotes in the new bank happen to be ON (if any); send LED ON messages to those only.
		(buttonNamesAsMidinotes + (bank * 16)).do({ |midinote|
			if(buttonArray[midinote].value==1, {
				this.prSendPadLEDs(midinote, \red);
			});
		});
	}

} // EOF
