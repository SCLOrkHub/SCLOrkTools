(
// Build Emoji data structures before building UI.
SCLOrkEmoji.load;

~w = Window.new("Emoji Picker Test",
	Rect.new(100, 100, 250, 100),
	false,
	true);
~w.layout = VLayout.new(
	HLayout.new(
		~showButton = Button.new,
		~hideButton = Button.new
	),
	nil
);

~picker = SCLOrkEmojiMenu.new(SCLOrkEmoji.trie.dictionary, "", true);

~showButton.string = "show";
~showButton.action = { ~picker.front };

~hideButton.string = "hide";

~w.front;
)

(
~w = Window.new("Emoji Picker Test",
	Rect.new(100, 100, 250, 100),
	false,
	true);
~w.layout = VLayout.new(
	~entryField = TextField.new);

~picker = SCLOrkEmojiPicker.new(Rect.new(
	~w.bounds.left, ~w.bounds.top + 100, ~w.bounds.width, ~w.bounds.height));
~oldAction = ~entryField.keyDownAction;
~entryField.keyDownAction = { |view, char, modifiers, unicode, keycode|
	{ ~picker.updateSearchTerm(~entryField.string) }.defer(0.05);
	~oldAction.value(view, char, modifiers, unicode, keycode);
};

~w.front;
)

(
SCLOrkEmoji.load;
~count = 0;
SCLOrkEmoji.trie.leafDoFrom(['p'], { |a, b, c|

	if (a.wrapAt(-1) === '_match' and: { ~count < 50 }, {
		"a: %, b: %, c: %".format(a.wrapAt(-1), b, c).postln;
		~count = ~count + 1;
	});
});
)

(
"texy".wrapAt(-1);
)