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

	var selectedPresetName;
	var selectedPreset;
	var selectedVoice;
	var voiceCodeTextView;
	var evalPbindefButton;

	*new { |
		playerNumber,
		presetSearchDir = "/home/sclork/Music/SCLOrk/Demos/PublicDomainTest" |
		^super.newCopyArgs(playerNumber, presetSearchDir).init;
	}

	init {
		presets = Dictionary.new;

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
		window = Window.new("PublicDomain: p" ++ playerNumber.asString,
			Rect.new(
				0,
				0,
				800,
				600));
		window.alwaysOnTop = true;

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
			voiceCodeTextView = TextView.new(),
			[ evalPbindefButton = Button.new(), align: \center ]
		);

		presetLabel.string = "Preset:";
		presetPopUp.action = { this.prPresetChanged };
		resetPresetButton.string = "Reset";
		resetPresetButton.action = { this.prPresetChanged };
		voiceNameLabel.string = "Voice Name:";
		bufnumLabel.string = "bufnum:";
		voiceCodeTextView.font = Font(Font.defaultMonoFace);
		evalPbindefButton.string = "Evaluate";
		evalPbindefButton.action = {
			voiceCodeTextView.string.interpret;
		};
	}

	prPopulatePresets {
		presetPopUp.items = presets.keys.asArray.sort;
	}

	prPresetChanged {
		selectedPresetName = presetPopUp.items[presetPopUp.value];
		selectedPreset = presets.at(selectedPresetName);
		selectedVoice = selectedPreset.voiceAt(playerNumber);
		voiceNameText.string = selectedVoice.name;
		bufnumText.string = selectedVoice.params.at('\\bufnum');
		voiceCodeTextView.string = selectedVoice.string;
	}
}
