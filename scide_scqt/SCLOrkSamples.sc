SCLOrkSamples {
	classvar <dict;
	classvar <>allowedExtensions;
	classvar <browseWindow;
	classvar synthNodesDict;

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
				("WARNING: " ++ key.asString ++ " already existed in the dictionary and is being overwritten.").postln;
			});
			// add to dict
			dict.put(key.asSymbol, buffer);
			[buffer.bufnum, key, path].postln;
		}, {
			[extension, "not a valid file extension"].postln;
		});
	} // end of loadFile


	// *loadFolder
	// takes a path to a folder containing samples
	// it's OK if folder contains sub-folders (but not sub-sub-folders)
	// samples will be loaded to Buffers
	// dictionary entries (keys) referring to Buffers will be named automatically using parent folder as main name, and then a number. For example, if a folder named "drums" contains three files called "snare.wav", "kick.wav", "hihat.wav", the dictionary will refer to them as \drums0, \drums1, \drums2. Files will be loaded in alphabetical order.


	*loadFolder { |path, time=0.0035|
		var p = PathName.new(path);

		Server.default.waitForBoot({

			// p = PathName.new(Quarks.folder ++ "/Dirt-Samples");

			Routine.run({
				var time = 0.0035;

				p.entries.do({ |thisEntry|
					if( thisEntry.isFile, {
						// loads a single file (if a valid extension)
						SCLOrkSamples.loadFile(thisEntry.fullPath);
						time.wait;
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

									if(dict[thisKey.asSymbol].notNil, {
										thisKey = (thisKey.asString ++ "b").asSymbol;
									});

									// now we are ready to load this file
									SCLOrkSamples.loadFile(thisFile.fullPath, thisKey);
									time.wait;
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

		if( (browseWindow.isNil), {

			var alphabeticalSamples, currentRoutine = nil, currentSynth = nil, currentButton = nil;

			alphabeticalSamples = dict.keys.asArray.sort; //copyRange(0, 250);
			browseWindow = Window.new(
				name: "Browse SCLOrkSamples [scroll down for more]",
				bounds: Rect(140, 0, 750, 290),
				resizable: true,
				scroll: true
			);
			browseWindow.onClose = ({ browseWindow = nil; "sample browser has been closed".postln; });
			browseWindow.view.decorator = FlowLayout(browseWindow.view.bounds, 5@5, 5@5);
			alphabeticalSamples.do({ |key| Button(browseWindow, 100@25)
				.states_([
					[key, Color.black, Color.white],
					[key, Color.white, Color.black]
				])
				.action_({ arg button;

					if(button.value==1,
						{
							var buffer, channels;

							buffer = dict.at(key);
							channels = buffer.numChannels;
							[buffer, channels, key].postln;

							if((buffer.notNil) && (channels.notNil), {

								// stop currentRoutine if there is one running (nil won't throw error)
								currentRoutine.stop;

								// free currentSynth if one exists
								if( currentSynth.notNil, {
									currentSynth.free; currentSynth = nil;
									currentButton.value = 0;
								});

								// create a Routine so that button can be turned off at end of synth
								currentRoutine = Routine.new({
										currentSynth = {
											PlayBuf.ar(
												numChannels:  channels,
												bufnum: buffer,
												rate: BufRateScale.kr(buffer),
												doneAction: 2
											)
										}.play;

										currentButton = button;
										(buffer.duration).wait;

										// clean up: turn button off, remove node from currentSynth
										button.value = 0;
										currentSynth = nil;

							}).play(clock: AppClock); // end of routine code
							}, {
								[key, "either buffer or numChannels was nil"].postln;
							});
						}, {
							// if user turns off a button directly,
							// stop currently playing synth, & stop routine too
							currentSynth.free; currentSynth = nil;
							currentRoutine.stop; currentRoutine = nil;
					});
				}); // end of .action
			}); // end of alphabeticalSamples.do (button creation)
			browseWindow.front;
		}, {
			browseWindow.unminimize;
			browseWindow.front;
		});


	}





} // end of SCLOrkSamples definition
