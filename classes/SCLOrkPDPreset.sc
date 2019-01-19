SCLOrkPDPreset {
	classvar <presets;

	var <presetName;
	var <voices;

	*addPreset { | preset |
		if (presets.isNil, {
			presets = Dictionary.new;
		});

		presets = presets.put(preset.presetName, preset);
	}

	*new { | presetName, voices |
		^super.newCopyArgs(presetName, voices).init;
	}

	*newFromFile { | presetName, filePath |
		// Read, then split file line-by-line, so we can rule out commented-out chunks
		var fileLines = String.readNew(File.new(filePath, "r")).split($\n);
		var inBlockComment = false;
		var voices = Array.new(8);

		fileLines.do({ | line, index |
			var col = 0;

			// If we're in block comment we simply scan until we are no longer in one.
			if (inBlockComment, {

			});
		});
	}

	init {
	}
}