SCLOrkPD {
	var playerNumber;
	var presetSearchDir;

	var presets;

	var window;
	var presetLabel;
	var presetPopUp;
	var resetPresetButton;
	var voiceNameLabel;
	var voiceNameText;
	var bufnumLabel;
	var bufnumText;
	var parameterScrollView;
	var parameterViews;
	var voiceCodeTextView;
	var evalPbindefButton;

	var selectedPresetName;
	var selectedPreset;
	var currentVoice;


	*new { |
		playerNumber,
		presetSearchDir = "/home/sclork/Music/SCLOrk/Demos/PublicDomainTest" |
		^super.newCopyArgs(playerNumber, presetSearchDir).init;
	}

	init {
		presets = IdentityDictionary.new;
		parameterViews = Array.new;

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
				presetLabel = StaticText.new(),
				presetPopUp = PopUpMenu.new(),
				nil,
                resetPresetButton = Button.new()
			),
			HLayout.new(
				voiceNameLabel = StaticText.new(),
				voiceNameText = StaticText.new(),
				nil
			),
			HLayout.new(
				bufnumLabel = StaticText.new(),
				bufnumText = StaticText.new(),
				nil
			),
			parameterScrollView = ScrollView.new(),
			voiceCodeTextView = TextView.new(),
			[ evalPbindefButton = Button.new(), align: \center ]
		);

		presetLabel.string = "Preset:";
		presetPopUp.action = { this.prPresetChanged };
		resetPresetButton.string = "Reset";
		resetPresetButton.action = { this.prPresetChanged };
		voiceNameLabel.string = "Voice Name:";
		bufnumLabel.string = "bufnum:";
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
					currentVoice.params.at(paramName));
				parameterScrollView.layout.add(paramView);
				parameterViews = parameterViews.add(paramView);
			});
		});

		parameterScrollView.layout.add(nil);
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
			this.prRebuildVoiceUI;
			^true;
		}, {
			voiceCodeTextView.stringColor = Color.red;
			^false;
		});
	}

	prAttemptInterpretEditedString {
		// Note: no error checking, and no feedback, either it worked
		// or it didn't. So good luck with that :).
		voiceCodeTextView.string.interpret;
	}

	prKeyDown { | char, modifiers |
		if (char == $\r or: { char == $\n }, {
			if (modifiers.isAlt, {
				// ALT+ENTER forces rebuild of UI part of code but does not
				// evaluate code.
				this.prAttemptRebuildFromEditedString;
			});
			if (modifiers.isCtrl, {
				if (this.prAttemptRebuildFromEditedString, {
					this.prAttemptInterpretEditedString;
				});
			});
		});
	}
}
