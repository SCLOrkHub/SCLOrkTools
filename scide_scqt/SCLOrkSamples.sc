SCLOrkSamples {
	classvar <dict;
	classvar <>allowedExtensions;
	classvar <browseWindow;


	*initClass {

		allowedExtensions = List[
			"wav", "WAV",
			"aif", "AIF", "aiff", "AIFF",
			"aifc", "AIFC",
			"flac", "FLAC"
		];

		dict = IdentityDictionary.new(n: 3000, know: true);
		dict.put(\r, \rest);

	}

	*clear {
		Buffer.freeAll;
		SCLOrkSamples.initClass;
	}


	// *loadFile
	// Loads a single file. File name becomes dictionary key, unless a custom key is provided.

	// TIPS FOR SAFE FILE NAMES:
	// only letters and numbers, dash or underscore as separators are OK
	// no spaces or other characters (punctuation etc)
	// make sure first character is a lowercase letter
	// first character cannot be a number

	/*
	key = (thisFolder.folderName ++ count).asString;
	key = if(key[0].asString.asInteger==0, { key }, {"a"++key});
	key = key.asSymbol;
	*/

	*loadFile { |path, key|
		var buffer, extension;

		extension = PathName(path).extension;

		// only proceed if file is valid (allowed extension)
		if(allowedExtensions.includesEqual(extension), {
			// if a key is not provided, use file name
			if(key.isNil, {
				key = PathName(path).fileNameWithoutExtension;
			});
			// We want to ensure first character is lowercase
			// (so we can take advantage of d.sampleName syntax)
			key = key.asString;
			key = key.first.toLower ++ key.drop(1);
			// load buffer
			buffer = Buffer.read(
				server: Server.default,
				path: path
			);
			// does entry already exist? Post a warning if yes
			if(dict[key.asSymbol].notNil, {
				("WARNING: " ++ key.asString ++ " already exists in the dictionary but will now be overwritten.").postln;
			});
			// add to dict
			dict.put(key.asSymbol, buffer);
			[buffer.bufnum, key, path].postln;
		}, {
			[extension, "not a valid file extension"].postln;
		});



	}


	// *loadFolder
	// takes a path to a folder containing samples
	// it's OK if folder contains sub-folders (but not sub-sub-folders)
	// samples will be loaded to Buffers
	// dictionary entries (keys) referring to Buffers will be named automatically using parent folder as main name, and then a number. For example, if a folder named "drums" contains three files called "snare.wav", "kick.wav", "hihat.wav", the dictionary will refer to them as \drums0, \drums1, \drums2. Files will be loaded in alphabetical order.


	*loadFolder { |path|
		var p = PathName.new(path);

		Server.default.waitForBoot({

			// p = PathName.new(Quarks.folder ++ "/Dirt-Samples");

			Routine.run({

				p.entries.do({ |thisEntry|
					if( thisEntry.isFile, {
						// loads a single file (if a valid extension)
						SCLOrkSamples.loadFile(thisEntry.fullPath);
						0.002.wait;
					}, {
						if( thisEntry.isFolder, {
							var thisFolder = thisEntry;
							thisFolder.entries.do({ |thisFile, count|
								var thisKey, buffer, extension;
								// make sure this is a file, not a sub-sub-folder
								// we don't accept sub-sub-folders! ;-)
								if( thisFile.isFile, {
									// use folder name for dict entry
									thisKey = (thisFolder.folderName ++ count).asString;
									// make sure first character is NOT a number
									// if it is, add letter 'a' to begin the name
									thisKey = if(thisKey[0].asString.asInteger==0, { thisKey }, {"a"++thisKey});
									thisKey = thisKey.asSymbol;
									// now we are ready to load this file
									SCLOrkSamples.loadFile(thisFile.fullPath, thisKey);
									0.002.wait;
								}, {
									"**Warning: " ++ thisFile.asString ++ " is not a file! No sub-sub-folders accepted".postln;
								});
							});
						});
					});
				}); // end of p.entries.do
			}); // end of Routine
		}); // end of waitForBoot


		^dict;



	} // end of loadFolder

	*gui {

		/*
		what this file does:

		Assuming Dirt samples are loaded into a dictionary saved under variable 'd',

		this file creates a GUI to browse all samples by name

		*/

		// Window.closeAll;
		if( (browseWindow.isNil), {

			var alphabeticalSamples;

			alphabeticalSamples = dict.keys.asArray.sort; //copyRange(0, 250);
			browseWindow = Window.new(
				name: "Browse SCLOrkSamples [scroll down for more]",
				bounds: Rect(10, 10, 1265, 700),
				resizable: false,
				scroll: true
			);
			browseWindow.onClose = ({ browseWindow = nil; "sample browser has been closed".postln; });
			browseWindow.view.decorator = FlowLayout(browseWindow.view.bounds, 5@5, 5@5);
			alphabeticalSamples.do({ |key| Button(browseWindow, 100@25)
				.states_([
					[key, Color.black, Color.white],
					[key, Color.white, Color.black]
				])
				// .mouseDownAction_({ |b| b.valueAction = 1 })
				.action_({ arg button;
					var playingSynth, routine;

					playingSynth = ("playingSynth" ++ key.asString).asSymbol;

					if(button.value==1,
						{
							var buffer, channels;
							buffer = dict.at(key);
							channels = buffer.numChannels;
							[buffer, channels, key].postln;
							if((buffer.notNil) && (channels.notNil), {
								routine = Routine.run(
									func: {
										Ndef(playingSynth, {
											Limiter.ar(
												in: PlayBuf.ar(
													numChannels:  channels,
													bufnum: buffer,
													rate: BufRateScale.kr(buffer)
												),
												level: 0.9)
										}).play;
										buffer.duration.wait;
										button.value = 0;
										if( Ndef(playingSynth).source.notNil, {
											Ndef(playingSynth).clear;
										});
									},
									clock: AppClock
								);
							}, { [key, "nil stuff"].postln });
						}, {
							if(Ndef(playingSynth).source.notNil, { Ndef(playingSynth).clear(0.5) });
							routine.stop;
						}
					);
				}); // end of .action
			}); // end of alphabeticalSamples.do (button creation)
			browseWindow.front;
		}, {
			browseWindow.unminimize;
			browseWindow.front;
		});


	}





} // end of SCLOrkSamples definition


/*
if( d.isNil, {
s.waitForBoot({

p = PathName.new(Quarks.folder ++ "/Dirt-Samples");

d = IdentityDictionary.new(n: 2500, know: true); // know: true makes the dictionary interpret method calls as look ups.

// add a symbol \r for rests

d.put(\r, \rest);

Routine.run({

p.folders.do({ |thisFolder|

thisFolder.entries.do({ |thisFile, count|
var key, buffer, extension, allowedExtensions;
extension = thisFile.extension;
allowedExtensions = ["wav", "WAV", "aif", "AIF", "aiff", "AIFF", "aifc", "AIFC"];
if(allowedExtensions.includesEqual(extension), {
key = (thisFolder.folderName ++ count).asString;
key = if(key[0].asString.asInteger==0, { key }, {"a"++key});
key = key.asSymbol;
buffer = Buffer.read(
server: s,
path: thisFile.fullPath
);
// s.sync;
0.015.wait;
[buffer.bufnum, key].postln;
d.put(key, buffer);
}, {
[extension, "not a wav or aif file"].postln;
});
});
});
});
}); // end of waitForBoot
}, {
"Samples already loaded.".postln;
"Variable 'd' should contain an Identity Dictionary with % entries:\n".postf(d.size);
d.postln;
}); // end of if