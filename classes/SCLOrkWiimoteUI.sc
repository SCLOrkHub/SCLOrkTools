SCLOrkWiimoteUI {
	var window;
	var statusText;
	var connectButton;
	var rumbleButton;
	var batteryLevelText;
	var ledCheckBoxes;
	var dPadLeftText;
	var dPadRightText;
	var dPadUpText;
	var dPadDownText;
	var aText;
	var bText;
	var plusText;
	var minusText;
	var oneText;
	var twoText;
	var homeText;
	var enableScopesCheckBox;
	var xyzScope;

	var autoConnectTask;

	var <wiimote;

	*new {
		^super.new.init;
	}

	init {
		this.prConstructUIElements;
		this.prConnectUI;
		this.prStartAutoconnect;
	}

	prStartAutoconnect {
		if (autoConnectTask.isNil, {
			autoConnectTask = SkipJack.new({
				if (wiimote.isNil and: { SCLOrkWiimote.isConnected }, {
					wiimote = SCLOrkWiimote.new;
					// Populate UI after short delay to let battery and led
					// status fields propagate.
					AppClock.sched(0.2, { this.prConnectUI; });
				});
			},
			dt: 0.2,
			stopTest: { wiimote.notNil },
			);
		});
	}

	prConstructUIElements {
		var ledCheck0, ledCheck1, ledCheck2, ledCheck3;

		window = SCLOrkWindow.new("Wiimote",
			Rect.new(0, 0, 500, 500)
		);
		window.alwaysOnTop = true;
		window.layout = VLayout.new(
			HLayout.new(
				StaticText.new.string_("status:"),
				statusText = StaticText.new.string_("scanning"),
				connectButton = Button.new.string_("connect")
			),
			HLayout.new(
				StaticText.new.string_("Battery:"),
				batteryLevelText = StaticText.new.string_("0%"),
				rumbleButton = Button.new.string_("rumble");
			),
			HLayout.new(
				StaticText.new.string_("LEDs:"),
				ledCheck0 = CheckBox.new,
				ledCheck1 = CheckBox.new,
				ledCheck2 = CheckBox.new,
				ledCheck3 = CheckBox.new
			),
			HLayout.new(
				dPadLeftText = StaticText.new.string_("L").background_(
					Color.white).align_(\center),
				VLayout.new(
					dPadUpText = StaticText.new.string_("U").background_(
						Color.white).align_(\center),
					dPadDownText = StaticText.new.string_("D").background_(
						Color.white).align_(\center),
				),
				dPadRightText = StaticText.new.string_("R").background_(
					Color.white).align_(\center),
				bText = StaticText.new.string_("b").background_(
					Color.white).align_(\center),
				aText = StaticText.new.string_("a").background_(
					Color.white).align_(\center),
				VLayout.new(
					plusText = StaticText.new.string_("+").background_(
						Color.white).align_(\center),
					homeText = StaticText.new.string_("home").background_(
						Color.white).align_(\center),
					minusText = StaticText.new.string_("-").background_(
						Color.white).align_(\center),
				),
		        oneText = StaticText.new.string_("1").background_(
					Color.white).align_(\center),
 				twoText = StaticText.new.string_("2").background_(
					Color.white).align_(\center)
			),
			enableScopesCheckBox = CheckBox.new.string_(
				"enable scopes").value_(false),
			xyzScope = ScopeView.new,
			nil
		);

		ledCheckBoxes = [ ledCheck0, ledCheck1, ledCheck2, ledCheck3 ];

		window.front;
	}

	prConnectUI {
		if (wiimote.notNil, {
			wiimote.getStatus;

			statusText.string = "connected";

			connectButton.enabled = false;

			rumbleButton.enabled = true;
			rumbleButton.mouseDownAction = {
				wiimote.rumble = true;
			};
			rumbleButton.action = {
				wiimote.rumble = false;
			};

			batteryLevelText.enabled = true;
			batteryLevelText.string = (
				wiimote.batteryLevel * 100
			).asInteger.asString ++ "%";

			ledCheckBoxes.do({ | ele, idx |
				ele.enabled = true;
				if (wiimote.leds.bitAnd(1 << idx) != 0, {
					ele.value = true;
				}, {
					ele.value = false;
				});
				ele.action = { | v |
					if (v.value, {
						wiimote.leds = wiimote.leds.bitOr(1 << idx);
					}, {
						wiimote.leds = wiimote.leds.bitAnd(
							(1 << idx).bitNot.bitAnd(0x0f));
					});
				};
			});

			wiimote.onButton = { | button, value |
				AppClock.play({
					var bg;
					if (value, {
						bg = Color.black;
					}, {
						bg = Color.white;
					});
					switch(button,
						\dPadLeft, { dPadLeftText.background = bg },
						\dPadRight, { dPadRightText.background = bg },
						\dPadDown, { dPadDownText.background = bg },
						\dPadUp, { dPadUpText.background = bg },
						\plus, { plusText.background = bg },
						\two, { twoText.background = bg },
						\one, { oneText.background = bg },
						\b, { bText.background = bg },
						\a, { aText.background = bg },
						\minus, { minusText.background = bg },
						\home, { homeText.background = bg }
					);
					nil;
				});
			};

			dPadLeftText.enabled = true;
			dPadRightText.enabled = true;
			dPadUpText.enabled = true;
			dPadDownText.enabled = true;
			aText.enabled = true;
			bText.enabled = true;
			plusText.enabled = true;
			minusText.enabled = true;
			oneText.enabled = true;
			twoText.enabled = true;
			homeText.enabled = true;
		}, {
			statusText.string = "scanning";
			connectButton.action = {
				this.prStartAutoconnect;
			};
			rumbleButton.enabled = false;
			batteryLevelText.enabled = false;
			batteryLevelText.string = "n/a";
			ledCheckBoxes.do({ | ele, idx |
				ele.enabled = false;
			});
			dPadLeftText.enabled = false;
			dPadRightText.enabled = false;
			dPadUpText.enabled = false;
			dPadDownText.enabled = false;
			aText.enabled = false;
			bText.enabled = false;
			plusText.enabled = false;
			minusText.enabled = false;
			oneText.enabled = false;
			twoText.enabled = false;
			homeText.enabled = false;
			enableScopesCheckBox.enabled = false;
			xyzScope.enabled = false;
		});
	}
}