SCLOrkSoundFile {
	var server;
	var window;
	var soundFileView;
	var startLabel;
	var durationLabel;
	var pathLabel;
	var numChannelsLabel;
	var sampleRateLabel;
	var fileDurationLabel;
	var openFileButton;
	var soundFile;
	var soundStartPos;
	var soundDuration;
	var soundBuffer;

	*new { |server|
		if (server.isNil, {
			server = Server.default;
		});
		if (server.pid.isNil, {
			"*** start audio server before SCLOrkSoundFile, please".postln;
			^nil;
		});
		^super.newCopyArgs(server).init;
	}

	init {
		window = SCLOrkWindow.new("Sound File",
			Rect.new(0, 0, 800, 500));
		window.alwaysOnTop = true;

		window.view.keyDownAction_({ | view, char, mod, unicode, keycode |
			if (char == $ , {
				Ndef('SCLOrkSoundFile', {
					var env, snd;
					env = Env(
						levels: [0, 1, 0],
						times: [0.001, soundDuration, 0.001],
					);
					snd = PlayBuf.ar(
						soundFile.numChannels,
						soundBuffer,
						BufRateScale.kr(soundBuffer),
						startPos: soundStartPos
					);
					snd * EnvGen.kr(env, doneAction: Done.freeSelf);
				}).fadeTime_(0).play;
				true;
			}, {
				false;
			});
		});

		window.layout = VLayout.new(
			HLayout.new(
				StaticText.new.string_("SHIFT + Right Click + Mouse Up or Down to Zoom"),
				StaticText.new.string_("Right Click + Mouse Left or Right to Scroll"),
			),
			soundFileView = SoundFileView.new,
			HLayout.new(
				StaticText.new.string_("selection start:"),
				startLabel = StaticText.new
			),
			HLayout.new(
				StaticText.new.string_("selection duration:"),
				durationLabel = StaticText.new
			),
			HLayout.new(
				StaticText.new.string_("path:"),
				pathLabel = StaticText.new
			),
			HLayout.new(
				StaticText.new.string_("number of channels:"),
				numChannelsLabel = StaticText.new
			),
			HLayout.new(
				StaticText.new.string_("sample rate:"),
				sampleRateLabel = StaticText.new
			),
			HLayout.new(
				StaticText.new.string_("duration (s):"),
				fileDurationLabel = StaticText.new
			),
			openFileButton = Button.new.string_("open file")
		);

		soundFileView.action = { this.updateSelectionInfo };

		openFileButton.action = {
			FileDialog.new({ |paths| this.openFile(paths[0]) }, { });
		};

		window.front;
	}

	openFile { |path|
		soundFile = SoundFile.openRead(path);
		if (soundFile.isNil, {
			"*** error reading sound file %".format(path).postln;
			soundFileView.soundfile = nil;
			startLabel.string = "";
			durationLabel.string = "";
			pathLabel.string = "";
			numChannelsLabel.string = "";
			sampleRateLabel.string = "";
			fileDurationLabel.string = "";
		}, {
			soundFileView.soundfile = soundFile;
			soundFileView.read(0, soundFile.numFrames);
			soundFileView.refresh;
			pathLabel.string = path;
			numChannelsLabel.string = soundFile.numChannels;
			sampleRateLabel.string = soundFile.sampleRate;
			fileDurationLabel.string = soundFile.numFrames / soundFile.sampleRate;
			soundStartPos = 0;
			soundDuration = soundFile.numFrames / soundFile.sampleRate;
			soundBuffer = Buffer.read(server, path);
		});

		startLabel.string = "";
		durationLabel.string = "";
	}

	updateSelectionInfo {
		if (soundFile.isNil, {
			startLabel.string = "";
			durationLabel.string = "";
		}, {
			var selection = soundFileView.selection(0);
			// Selection is array of [ start, length ] where both units are in frames.
			var startTime = selection[0] / soundFile.sampleRate;
			var startNorm = selection[0] / soundFile.numFrames;
			var durationTime = selection[1] / soundFile.sampleRate;
			var durationNorm = selection[1] / soundFile.numFrames;
			if (durationTime > 0.0, {
				startLabel.string = "% s, % norm".format(startTime, startNorm);
				durationLabel.string = "% s, % norm".format(durationTime, durationNorm);
				soundStartPos = startNorm;
				soundDuration = durationTime;
			}, {
				startLabel.string = "";
				durationLabel.string = "";
				soundStartPos = 0;
				soundDuration = soundFile.numFrames / soundFile.sampleRate;
			});
		});
	}
}