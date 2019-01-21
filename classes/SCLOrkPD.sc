SCLOrkPD {
	var playerNumber;
	var presetSearchDir;

	var presets;
	var window;

	*new { | playerNumber, presetSearchDir = "/home/sclork/Music/SCLOrk/Demos/PublicDomainTest" |
		^super.newCopyArgs(playerNumber, presetSearchDir).init;
	}

	init {
		presets = IdentityDictionary.new;

		this.prParsePresets;
		this.prConstructUIElements;
	}

	prParsePresets {
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
						"successfully parsed: %!".format(pathName.asAbsolutePath).postln;
					}, {
						"*** error parsing %".format(pathName.asAbsolutePath).postln;
					});
			});
		});
	}

	prConstructUIElements {
		window = Window.new("PublicDomain: p" ++ playerNumber.asString,
			Rect.new(
				0,
				0,
				800,
				600));
	}
}
