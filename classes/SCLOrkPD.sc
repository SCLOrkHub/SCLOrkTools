SCLOrkPD {
	var playerNumber;
	var presetSearchDir;

	var presets;

	var window;
	var presetPopUp;
	var resetPresetButton;
	var voiceNameText;
	var bufnumText;
	var namePlayingText;
	var parameterScrollView;
	var parameterViews;
	var voiceCodeTextView;
	var evalPbindefButton;

	var selectedPresetName;
	var selectedPreset;
	var currentVoice;

	var voiceError;
	var namePlaying;

	*new { |
		playerNumber,
		presetSearchDir = "/home/sclork/Music/SCLOrk/Demos/PublicDomainTest" |
		^super.newCopyArgs(playerNumber, presetSearchDir).init;
	}

	init {
		presets = IdentityDictionary.new;
		parameterViews = Array.new;
		voiceError = false;

		this.prParsePresets;
		this.prConstructUIElements;
		this.prPopulatePresets;

		window.front;
	}

	prParsePresets {
		var parseFailCount = 0;
		var rootPath = PathName.new(presetSearchDir);
		rootPath.filesDo({ | pathName |
			if (pathName.isFile
				and: { pathName.extension == "scd" }
				and: { pathName.fileName.beginsWith("PD_Preset_") }, {
					var preset;
					preset = SCLOrkPDPreset.newFromFile(
						pathName.asAbsolutePath,
						pathName.fileNameWithoutExtension["PD_Preset_".size..]);
					if (preset.notNil, {
						presets.put(preset.name, preset);
						"parsed: %".format(pathName.asAbsolutePath).postln;
					}, {
						"*** error parsing %".format(pathName.asAbsolutePath).postln;
						parseFailCount = parseFailCount + 1;
					});
			});
		});

		"SCLOrkPD parsed % out of % files".format(
			presets.size, presets.size + parseFailCount).postln;
	}

	prConstructUIElements {
		var scrollCanvas;

		window = Window.new("PublicDomain - player " ++ playerNumber.asString,
			Rect.new(
				0,
				0,
				1080,
				1080));
		window.alwaysOnTop = true;
		window.view.keyDownAction_({ | view, char, modifiers, unicode, keycode |
			this.prKeyDown(char, modifiers);
		});

		window.layout = VLayout.new(
			HLayout.new(
				StaticText.new().string_("Preset:"),
				presetPopUp = PopUpMenu.new(),
				nil,
				StaticText.new().string_("(ctrl+r)"),
                resetPresetButton = Button.new()
			),
			HLayout.new(
				StaticText.new().string_("Voice Name:"),
				voiceNameText = StaticText.new(),
				nil,
				StaticText.new().string_("Now Playing:"),
				namePlayingText = StaticText.new().string_("none")
			),
			HLayout.new(
				StaticText.new().string_("\\bufum:"),
				bufnumText = StaticText.new(),
				nil
			),
			parameterScrollView = ScrollView.new(),
			voiceCodeTextView = TextView.new(),
			HLayout.new(
				nil,
				[ StaticText.new().string_("(ctrl+enter)"), align: \center ],
				[ evalPbindefButton = Button.new(), align: \center ],
				nil
			)
		);

		presetPopUp.action = { this.prPresetChanged };
		resetPresetButton.string = "Reset";
		resetPresetButton.action = {
			this.prPresetChanged;
			this.prAttemptInterpretEditedString;
		};
		scrollCanvas = View();
		scrollCanvas.layout = VLayout();
		parameterScrollView.canvas = scrollCanvas;
		parameterScrollView.background = Color.new(0.7, 0.7, 0.7);
		voiceCodeTextView.font = Font(Font.defaultMonoFace);
		voiceCodeTextView.editable = true;
		voiceCodeTextView.enterInterpretsSelection = false;

		evalPbindefButton.string = "Play";
		evalPbindefButton.action = {
			if (this.prAttemptRebuildFromEditedString, {
				this.prAttemptInterpretEditedString;
			});
		};
	}

	prPopulatePresets {
		presetPopUp.items = presets.keys.asArray.sort;
	}

	prPresetChanged {
		selectedPresetName = presetPopUp.items[presetPopUp.value];
		selectedPreset = presets.at(selectedPresetName);
		currentVoice = selectedPreset.voiceAt(playerNumber).deepCopy;
		this.prRebuildVoiceUI;
	}

	prRebuildVoiceUI {
		var orderedParamNames = Array.newClear(currentVoice.params.size);
		var paramViewCount = 0;
		voiceNameText.string = currentVoice.name;
		bufnumText.string = currentVoice.params.at('\\bufnum').at(\string);
		voiceCodeTextView.string = currentVoice.string;
		if (voiceError.not, {
			voiceCodeTextView.stringColor = Color.black;
		}, {
			voiceCodeTextView.stringColor = Color.red;
		});
		parameterViews.do({ | view | view.remove; });
		parameterScrollView.layout.clear;
		// We build the views out-of-order, but populate them in the correct
		// order in the array, so they can be added in same order as in code.
		parameterViews = Array.new(currentVoice.params.size);
		currentVoice.params.keysValuesDo({ | paramName, paramValue |
			if (paramName === '\\bufnum'
				or: { paramName === '\\instrument' }, {
					// Can do a sample PopUp picker later, but for now the
					// buffer can only be changed in code and shows up as
					// a label above, so we skip.

					// Instrument is hard-coded for now, no picker.
			}, {
					orderedParamNames[paramValue.at(\order)] = paramName;
			});
		});

		orderedParamNames.do({ | paramName |
			if (paramName.notNil, {
				var paramView = SCLOrkPDParameterView.new(
					parameterScrollView.canvas,
					paramName,
					currentVoice.params.at(paramName)
				);
				paramView.action = { | view |
					this.prUIChangedParameter(view);
				};
				parameterScrollView.layout.add(paramView);
				parameterViews = parameterViews.add(paramView);
			});
		});

		parameterScrollView.layout.add(nil);
	}

	prUIChangedParameter { | view |
		currentVoice.params.put(view.name, view.value);
		currentVoice.rebuildString;
		voiceError = false;
		voiceCodeTextView.stringColor = Color.black;
		voiceCodeTextView.string = currentVoice.string;
	}

	prAttemptRebuildFromEditedString {
		var tokens = SCLOrkPDParser.tokenize(voiceCodeTextView.string);
		var newVoice = nil;

		if (tokens.notNil, {
			newVoice = SCLOrkPDVoice.newFromTokens(tokens);
		});

		if (newVoice.notNil, {
			currentVoice = newVoice;
			voiceCodeTextView.stringColor = Color.black;
			voiceError = false;
			this.prRebuildVoiceUI;
			^true;
		}, {
			voiceError = true;
			voiceCodeTextView.stringColor = Color.red;
			^false;
		});
	}

	prAttemptInterpretEditedString {
		voiceCodeTextView.background = Color.black;
		voiceCodeTextView.stringColor = Color.white;
		AppClock.sched(0.1, {
			voiceCodeTextView.background = Color.white;
			if (voiceError.not, {
				voiceCodeTextView.stringColor = Color.black;
			}, {
				voiceCodeTextView.stringColor = Color.red;
			});
		});

		{ voiceCodeTextView.string.interpret; }.try({ | error |
			error.errorString.postln;
			voiceError = true;
		});

		if (voiceError.not, {
			// Stop any old playing voice, if there was one. This
			// can happen when there are more players than voices,
			// so voice number will change from preset to preset.
			if (namePlaying.notNil
				and: { namePlaying != currentVoice.name }, {
					var realName = namePlaying[1..namePlaying.size-1];
				Pbindef(realName.asSymbol).stop;
			});
			namePlaying = currentVoice.name;
			namePlayingText.string = currentVoice.name;
		});
	}

	prKeyDown { | char, modifiers |
		// "char: %, ascii: %, ctrl: %, alt: %".format(
		//	char, char.ascii, modifiers.isCtrl, modifiers.isAlt).postln;

		if (char == $\r or: { char == $\n }, {
			if (modifiers.isAlt, {
				// ALT+ENTER forces rebuild of UI part of code but does not
				// evaluate code.
				this.prAttemptRebuildFromEditedString;
			});
			if (modifiers.isCtrl, {
				if (voiceCodeTextView.string.compare(
					currentVoice.string) != 0, {
					this.prAttemptRebuildFromEditedString;
				});

				if (voiceError.not, {
					this.prAttemptInterpretEditedString;
				});
			});
		});

		// CTRL+R forces reset and eval of voice.
		if (char.ascii == 18 and: { modifiers.isCtrl }, {
			this.prPresetChanged;
			this.prAttemptInterpretEditedString;
		});

		// We forward CTRL+. to the GUI.
		if (char == $. and: { modifiers.isCtrl }, {
			ScIDE.cmdPeriod;
			namePlaying = nil;
			namePlayingText.string = "none";
		});
	}
}
