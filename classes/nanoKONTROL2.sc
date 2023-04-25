// ----------------------------------------------------------------------------
// KORG nanoKONTROL 2
//
// See README.md for more information.
// -----------------------------------
//
// The MIT License (MIT)
//
// Copyright (c) 2015 Marek Miller
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.
// ------------------------------------------------------------------------------



NanoKONTROL2 {

    // variables and default values
    var
    <server, <srcID, <num_of_scenes,
    <knobs_init_val, <faders_init_val,

    <>button_slow_factor,

    <>verbose,

    <>scene = 0,
    <knobs, <faders,
    <knobs_lastval, <faders_lastval,

    <sbuttons, <mbuttons, <rbuttons,

    <backwards_button, <forwards_button,
    <stop_button, <play_button, <rec_button,

    <>transport_action = nil,
    <>backwards_action = nil,
    <>forwards_action  = nil,
    <>stop_action = nil,
    <>play_action = nil,
    <>rec_action  = nil,

    mididef_key,

    <outport = nil,
    <midiOut = nil;




    // change this only if you are writing a class for
    // nanoKONTROL3 with 9 knobs and faders
    var
    <nk2num = 8,
    <knobs_note  = 16,
    <faders_note = 0,
    <sbutton_note = 32,
    <mbutton_note = 48,
    <rbutton_note = 64,
    <scene_dim_note = 58,
    <scene_inc_note = 59,
    <backwards_note = 43,
    <forwards_note = 44,
    <stop_note = 42,
    <play_note = 41,
    <rec_note = 45,
    <cycle_note = 46,
    <set_note = 60,
    <marker_left_note = 61,
    <marker_right_note = 62;



    *new { arg
        server,
        srcID = 1310720,
        outport = nil,
        num_of_scenes = 4,

        knobs_init_val = 0,
        faders_init_val = 0,
        button_slow_factor = 0.1,
        verbose = 0;

        ^super.new.initNanoKONTROL2(server,
            srcID, outport, num_of_scenes,
            knobs_init_val, faders_init_val,
            button_slow_factor,
            verbose
        );
    }


    initNanoKONTROL2 { arg arg_server,
            arg_srcID, arg_outport,
            arg_num_of_scenes,
            arg_knobs_init_val, arg_faders_init_val,
            arg_button_slow_factor, arg_verbose;


        server = arg_server;
        srcID = arg_srcID;
        outport = arg_outport;
        num_of_scenes = arg_num_of_scenes;
        knobs_init_val  = arg_knobs_init_val;
        faders_init_val = arg_faders_init_val;
        button_slow_factor = arg_button_slow_factor;
        verbose = arg_verbose;


        if ( (outport != nil), {
            midiOut = MIDIOut(outport);
            this.set_external_led_mode;
        });


        sbuttons = Array.fill(num_of_scenes, {arg i; Array.fill(nk2num, { arg j; NanoKONTROL2Button(sbutton_note + j, outport) }) });
        mbuttons = Array.fill(num_of_scenes, {arg i; Array.fill(nk2num, { arg j; NanoKONTROL2Button(mbutton_note + j, outport) }) });
        rbuttons = Array.fill(num_of_scenes, {arg i; Array.fill(nk2num, { arg j; NanoKONTROL2Button(rbutton_note + j, outport) }) });

        backwards_button = NanoKONTROL2Button(backwards_note, outport);
        forwards_button  = NanoKONTROL2Button(forwards_note, outport);
        stop_button =  NanoKONTROL2Button(stop_note, outport);
        play_button =  NanoKONTROL2Button(play_note, outport);
        rec_button  =  NanoKONTROL2Button(rec_note, outport);

        knobs = Array.fill(num_of_scenes, { arg i; Array.fill(nk2num, { arg j; NanoKONTROL2Knob(server, knobs_init_val, matched_led: sbuttons[i][j].led) }) });
        faders = Array.fill(num_of_scenes, { arg i; Array.fill(nk2num, { arg j; NanoKONTROL2Fader(server, faders_init_val, matched_led: mbuttons[i][j].led) }) });

        knobs_lastval = Array.fill(nk2num, { arg j; 0 });
        faders_lastval = Array.fill(nk2num, { arg j; 0 });

        mididef_key = "nK2_" ++ srcID.asString ++ "_default";
        this.nK2_mididef(mididef_key);

        // Welcoming lights!
        if ( (outport != nil), {
            Task( {
                8.do{ arg i;
                    sbuttons[scene][i].led.blink;
                    0.04.wait;
                };
                0.3.wait;
                8.do{ arg i;
                    mbuttons[scene][7-i].led.blink;
                    0.04.wait;
                };
                0.3.wait;
                8.do{ arg i;
                    rbuttons[scene][i].led.blink;
                    0.04.wait;
                };
            }).play;
        });


    }


    // ------------------------------------------------------------
    // define default MIDIdef for knobs and faders
    nK2_mididef { arg key;

        MIDIdef.cc(key,  { arg val, cc, chan, src;

            // change the scene
            if ( (cc == scene_dim_note) || (cc == scene_inc_note) && (val == 127), {

                if ( (cc == scene_dim_note), {
                    scene = (scene - 1 ) % num_of_scenes;
                    }, {
                    scene = (scene + 1 ) % num_of_scenes;
                });


                nk2num.do{ arg j;
                    knobs[scene][j].matched = 0;
                    faders[scene][j].matched = 0;
                };

                nk2num.do{ arg j;
                    if ( (knobs[scene][j].val == knobs_lastval[j]), {
                        knobs[scene][j].matched = 1;
                    });

                    if ( (faders[scene][j].val == faders_lastval[j]), {
                        faders[scene][j].matched = 1;
                    });
                };


                ("nK2, srcID: " ++ srcID.asString ++ ", current scene: " ++ scene.asString ++ "/" ++ num_of_scenes.asString ++ ".").postln;
            });


            // S button
            if ( (cc >= sbutton_note) && (cc <= (sbutton_note + nk2num - 1)), {

                sbuttons[scene][cc - sbutton_note].val = val.linlin(0,127,0,1);

                knobs[scene][cc - sbutton_note].tmpSval  = knobs[scene][cc - sbutton_note].prev;
                faders[scene][cc - sbutton_note].tmpSval = faders[scene][cc - sbutton_note].prev;

                if ( (val == 0), {
                    // then it must have been pressed and released just now
                    if ( (knobs_lastval[cc - sbutton_note] != knobs[scene][cc - sbutton_note].val), {
                        knobs[scene][cc - sbutton_note].matched = 0;
                    });
                    if ( (faders_lastval[cc - sbutton_note] != faders[scene][cc - sbutton_note].val), {
                        faders[scene][cc - sbutton_note].matched = 0;
                    });
                });

            });

            // M button
            if ( (cc >= mbutton_note) && (cc <= (mbutton_note + nk2num - 1)), {

                mbuttons[scene][cc - mbutton_note].val = val.linlin(0,127,0,1);

            });

            // R button
            if ( (cc >= rbutton_note) && (cc <= (rbutton_note + nk2num - 1)), {
                rbuttons[scene][cc - rbutton_note].val = val.linlin(0,127,0,1);

                if ( (val == 0), {
                    // then it must have been pressed and released just now
                    if ( (knobs_lastval[cc - rbutton_note] != knobs[scene][cc - rbutton_note].val), {
                        knobs[scene][cc - rbutton_note].matched = 0;
                    });
                    if ( (faders_lastval[cc - rbutton_note] != faders[scene][cc - rbutton_note].val), {
                        faders[scene][cc - rbutton_note].matched = 0;
                    });
                });
            });


            // knobs
            if ( (cc >= knobs_note) && (cc <= (knobs_note + nk2num - 1)), {

               var p = val.linlin(0,127,-1,1);
               knobs_lastval[cc - knobs_note] = p;

               if( ( knobs[scene][cc - knobs_note].matched == 1), {

                    if ( (rbuttons[scene][cc - knobs_note].val == 1), {
                        // TODO: smooth the randomness a little bit
                        p = 1.0.rand;
                    });

                    if ( (sbuttons[scene][cc - knobs_note].val == 1), {
                        p = (val.linlin(0,127,-1,1) - knobs[scene][cc - knobs_note].tmpSval)*button_slow_factor +
                             knobs[scene][cc - knobs_note].tmpSval;
                        p = max(-1,p);
                        p = min(p, 1);
                    });

                    if ( (mbuttons[scene][cc - knobs_note].val == 0), {
                        knobs[scene][cc - knobs_note].prev = p;

                       if( (this.verbose == 1), {
                            ("nK2, srcID: " ++ srcID.asString ++ ", scene: " ++ scene.asString ++ ", knob: " ++ (cc - knobs_note).asString ++ ", val: " ++ p).postln;
                       });
                    });

                }, {


                    if( ( (val + 3) >= knobs[scene][cc - knobs_note].prev.linlin(-1,1,0,127) ) && ((val - 3) <= knobs[scene][cc - knobs_note].prev.linlin(-1,1,0,127) ), {
                            knobs[scene][cc - knobs_note].matched = 1;
                    });

                });

                knobs[scene][cc - knobs_note].set_asprev;
            });


            // faders
            if ( (cc >= faders_note) && (cc <= (faders_note + nk2num - 1) ), {

               var p = val.linlin(0,127,0,1);
               faders_lastval[cc - faders_note] = p;

               if( ( faders[scene][cc - faders_note].matched == 1), {

                    if ( (rbuttons[scene][cc - faders_note].val == 1), {
                        p = 1.0.rand;
                    });

                    if ( (sbuttons[scene][cc - faders_note].val == 1), {
                        p = (val.linlin(0,127,0,1) - faders[scene][cc - faders_note].tmpSval)*button_slow_factor +
                             faders[scene][cc - faders_note].tmpSval;
                        p = max(0,p);
                        p = min(p, 1);
                    });

                    if ( (mbuttons[scene][cc - faders_note].val == 0), {
                        faders[scene][cc - faders_note].prev = p;

                        if( (this.verbose == 1), {
                            ("nK2, srcID: " ++ srcID.asString ++ ", scene: " ++ scene.asString ++ ", fader: " ++ (cc - faders_note).asString ++ ", val: " ++ p).postln;
                        });
                    });


                }, {

                    if( ( (val + 3) >= faders[scene][cc - faders_note].prev.linlin(0,1,0,127)) && ((val - 3) <= faders[scene][cc - faders_note].prev.linlin(0,1,0,127)), {
                            faders[scene][cc - faders_note].matched = 1;
                    });

                });

                faders[scene][cc - faders_note].set_asprev;

            });


            // Read current values of stop, play, rec buttons and switch leds appropriately
            // Execute custom action.
            case

            { cc == backwards_note } {
                backwards_button.val = val;

                if (val == 127) {
                    this.transport_action.value(scene);
                    this.backwards_action.value(scene);
                };

                backwards_button.led.change;
            }

            { cc == forwards_note } {
                forwards_button.val = val;

                if (val == 127) {
                    this.transport_action.value(scene);
                    this.forwards_action.value(scene);
                };

                forwards_button.led.change;
            }

            { cc == stop_note } {
                stop_button.val = val;

                if (val == 127) {
                    this.transport_action.value(scene);
                    this.stop_action.value(scene);
                    play_button.led.off;
                    rec_button.led.off;
                };

                stop_button.led.change;
            }

            { cc == play_note } {
                play_button.val = val;

                if (val == 127) {
                    this.transport_action.value(scene);
                    this.play_action.value(scene);
                    play_button.led.change;
                };
            }

            { cc == rec_note  } {
                rec_button.val = val;
                if (val == 127) {
                    this.transport_action.value(scene);
                    this.rec_action.value(scene);
                    rec_button.led.change;
                };
            };

        }, srcID: srcID);
    } // end of MIDIdef for knobs and faders
    // ------------------------------------------------------------


    // Put nK2 in the external LED mode
    // Taken from:
    // https://github.com/overtone/overtone/blob/master/src/overtone/device/midi/nanoKONTROL2.clj
    set_external_led_mode {
        if ( (midiOut != nil ), {

            midiOut.sysex(Int8Array[0xF0, 0x7E, 0x7F, 0x06, 0x01, 0xF7]);
            midiOut.sysex(Int8Array[0xF0, 0x42, 0x40, 0x00, 0x01, 0x13, 0x00, 0x1F, 0x12, 0x00, 0xF7]);
            midiOut.sysex(Int8Array[0xF0, 0x7E, 0x7F, 0x06, 0x01, 0xF7]);
            midiOut.sysex(Int8Array[0xF0, 0x42, 0x40, 0x00, 0x01, 0x13, 0x00, 0x7F, 0x7F, 0x02, 0x03, 0x05, 0x40, 0x00, 0x00, 0x00,
    0x01, 0x10, 0x01, 0x00, 0x00, 0x00, 0x00, 0x7F, 0x00, 0x01, 0x00, 0x10, 0x00, 0x00, 0x7F, 0x00,
    0x01, 0x00, 0x20, 0x00, 0x7F, 0x00, 0x00, 0x01, 0x00, 0x30, 0x00, 0x7F, 0x00, 0x00, 0x01, 0x00,
    0x40, 0x00, 0x7F, 0x00, 0x10, 0x00, 0x01, 0x00, 0x01, 0x00, 0x7F, 0x00, 0x01, 0x00, 0x00, 0x11,
    0x00, 0x7F, 0x00, 0x01, 0x00, 0x00, 0x21, 0x00, 0x7F, 0x00, 0x01, 0x00, 0x31, 0x00, 0x00, 0x7F,
    0x00, 0x01, 0x00, 0x41, 0x00, 0x00, 0x7F, 0x00, 0x10, 0x01, 0x00, 0x02, 0x00, 0x00, 0x7F, 0x00,
    0x01, 0x00, 0x12, 0x00, 0x7F, 0x00, 0x00, 0x01, 0x00, 0x22, 0x00, 0x7F, 0x00, 0x00, 0x01, 0x00,
    0x32, 0x00, 0x7F, 0x00, 0x01, 0x00, 0x00, 0x42, 0x00, 0x7F, 0x00, 0x10, 0x01, 0x00, 0x00, 0x03,
    0x00, 0x7F, 0x00, 0x01, 0x00, 0x00, 0x13, 0x00, 0x7F, 0x00, 0x01, 0x00, 0x23, 0x00, 0x00, 0x7F,
    0x00, 0x01, 0x00, 0x33, 0x00, 0x00, 0x7F, 0x00, 0x01, 0x00, 0x43, 0x00, 0x7F, 0x00, 0x00, 0x10,
    0x01, 0x00, 0x04, 0x00, 0x7F, 0x00, 0x00, 0x01, 0x00, 0x14, 0x00, 0x7F, 0x00, 0x00, 0x01, 0x00,
    0x24, 0x00, 0x7F, 0x00, 0x01, 0x00, 0x00, 0x34, 0x00, 0x7F, 0x00, 0x01, 0x00, 0x00, 0x44, 0x00,
    0x7F, 0x00, 0x10, 0x01, 0x00, 0x00, 0x05, 0x00, 0x7F, 0x00, 0x01, 0x00, 0x15, 0x00, 0x00, 0x7F,
    0x00, 0x01, 0x00, 0x25, 0x00, 0x00, 0x7F, 0x00, 0x01, 0x00, 0x35, 0x00, 0x7F, 0x00, 0x00, 0x01,
    0x00, 0x45, 0x00, 0x7F, 0x00, 0x00, 0x10, 0x01, 0x00, 0x06, 0x00, 0x7F, 0x00, 0x00, 0x01, 0x00,
    0x16, 0x00, 0x7F, 0x00, 0x01, 0x00, 0x00, 0x26, 0x00, 0x7F, 0x00, 0x01, 0x00, 0x00, 0x36, 0x00,
    0x7F, 0x00, 0x01, 0x00, 0x46, 0x00, 0x00, 0x7F, 0x00, 0x10, 0x01, 0x00, 0x07, 0x00, 0x00, 0x7F,
    0x00, 0x01, 0x00, 0x17, 0x00, 0x00, 0x7F, 0x00, 0x01, 0x00, 0x27, 0x00, 0x7F, 0x00, 0x00, 0x01,
    0x00, 0x37, 0x00, 0x7F, 0x00, 0x00, 0x01, 0x00, 0x47, 0x00, 0x7F, 0x00, 0x10, 0x00, 0x01, 0x00,
    0x3A, 0x00, 0x7F, 0x00, 0x01, 0x00, 0x00, 0x3B, 0x00, 0x7F, 0x00, 0x01, 0x00, 0x00, 0x2E, 0x00,
    0x7F, 0x00, 0x01, 0x00, 0x3C, 0x00, 0x00, 0x7F, 0x00, 0x01, 0x00, 0x3D, 0x00, 0x00, 0x7F, 0x00,
    0x01, 0x00, 0x3E, 0x00, 0x7F, 0x00, 0x00, 0x01, 0x00, 0x2B, 0x00, 0x7F, 0x00, 0x00, 0x01, 0x00,
    0x2C, 0x00, 0x7F, 0x00, 0x01, 0x00, 0x00, 0x2A, 0x00, 0x7F, 0x00, 0x01, 0x00, 0x00, 0x29, 0x00,
    0x7F, 0x00, 0x01, 0x00, 0x2D, 0x00, 0x00, 0x7F, 0x00, 0x7F, 0x7F, 0x7F, 0x7F, 0x00, 0x7F, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0xF7, 0xF0, 0x7E, 0x7F, 0x06, 0x01, 0xF7]);
            midiOut.sysex(Int8Array[0xF0, 0x7E, 0x7F, 0x06, 0x01, 0xF7]);
            midiOut.sysex(Int8Array[0xF0, 0x42, 0x40, 0x00, 0x01, 0x13, 0x00, 0x1F, 0x11, 0x00, 0xF7]);

        });

    }


    dumpScene{ arg scenenum;

        if ( (scenenum >= 0) && (scenenum < num_of_scenes), {
            ("nK2, srcID: " ++ srcID.asString ++ " scene: " ++ scenenum.asString ++ "/" ++ num_of_scenes.asString ++ ".").postln;
            nk2num.do{ arg j;
                ("knob" ++ j.asString ++ ": " ++ knobs[scenenum][j].val ++ ", fader" ++ j.asString ++ ": " ++ faders[scenenum][j].val).postln;
            }
        });

    }


    dumpCurrentScene{
        this.dumpScene(scene);
    }


    dumpAll {

        ("nK2, srcID: " ++ srcID.asString).postln;

        num_of_scenes.do{ arg i;
            nk2num.do{ arg j;
                ("scene: " ++ i.asString ++ ", knob" ++ j.asString ++ ": " ++ knobs[i][j].val ++ ", fader" ++ j.asString ++ ": " ++ faders[i][j].val).postln;
            }
        }
    }





    free {
        MIDIdef(mididef_key).free;

        num_of_scenes.do{ arg i;
                    nk2num.do{ arg j;
                        knobs[i][j].free;
                        faders[i][j].free;
                    };
        };

        ^super.free;
    }

}



NanoKONTROL2Control {

    var
    <server,
    <init_val,
    <val = nil, <>prev, <matched = 0,
    <bus,

    <>tmpSval = 0,
    <matched_led = nil;

    *new { arg server, init_val = 0, matched_led = nil;
        ^super.new.initNK2Control(server, init_val, matched_led);
    }

    initNK2Control { arg arg_server, arg_init_val, arg_matched_led;
        server = arg_server;
        init_val = arg_init_val;
        matched_led = arg_matched_led;

        prev = init_val;
        // val = init_val;
        bus = Bus.control(server, 1);
        bus.set(init_val);
    }

    matched_ { arg i;

        matched = i;
        if ( (matched_led != nil), {
            if ( (matched == 0), { matched_led.off; });
            if ( (matched == 1), { matched_led.on; });
        });
    }

    val_{ arg v;
        val = v;
        bus.set(v);
    }

    set_asprev {
        val = prev;
        bus.set(prev);
    }

    kr {
        ^bus.kr;
    }

    free {
        bus.free;
        ^super.free;
    }


}

// To be expanded later...

NanoKONTROL2Knob : NanoKONTROL2Control {}

NanoKONTROL2Fader : NanoKONTROL2Control {}


NanoKONTROL2Button {

    var <note;
    var <>val = 0;
    var <led;

    *new { arg note, outport = nil;
        ^super.new.initButton(note, outport);
    }


    initButton { arg arg_note, arg_outport;

        note = arg_note;
        led = NanoKONTROL2LED(arg_note, arg_outport, false);

    }

}


NanoKONTROL2LED {

    var <note, <outport, <ison = false;

    *new { arg note, outport = nil, ison = false;
        ^super.new.initLED(note, outport, ison);
    }

    initLED {arg arg_note, arg_outport, arg_ison;
        note = arg_note;
        outport = arg_outport;
        ison = arg_ison;

        if ( (ison != false), {
             ison.postln;
            this.on;
        }, {
            this.off;
        });
    }


    on {
        if ( (outport != nil), {
            MIDIOut(outport).control(0, note, 127);
            ison = true;
        });
    }


    off {
        if ( (outport != nil), {
            MIDIOut(outport).control(0, note, 0);
            ison = false;
        });
    }


    change {
        if ( (ison != false), {
            this.off;
        }, {
            this.on;
        });

    }


    blink {

        Task({
            5.do{ this.change;  0.2.wait; };
            this.change;
        }).play;

    }

}
