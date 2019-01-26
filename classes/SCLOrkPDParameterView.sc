SCLOrkPDParameterView : View {
	const editCharacterWidth = 18;
	var <name;
	var <value;

	*new { | parent, parameterName, parameterValue |
		^super.new(parent).init(parameterName, parameterValue);
	}

	init { | parameterName, parameterValue |
		var subView, currentLayout, labelString, lineHeight, totalHeight;

		name = parameterName;
		value = parameterValue;
		this.layout = VLayout().spacing_(0).margins_(0);
		currentLayout = HLayout().spacing_(0).margins_(0);

		// Add label of parameter name.
		subView = StaticText.new(this);
		subView.string = parameterName.asString ++ ":";
		subView.font = Font(Font.defaultSansFace, bold: true);
		currentLayout.add(subView);
		labelString = "";
		lineHeight = 0;
		totalHeight = 0;

		// NumberBox
		// RangeSlider
		// ScopeView
		// Slider
		// SoundFileView

		parameterValue.at(\tokens).do({ | token, index |
			// At this point only \number fields are editable, so we
			// treat all other fields as non-editable. If a field is
			// not editable its string is concatenated on to labelString
			// until there is a line break. A line break or an editable
			// field causes the concatenated label to be added as a
			// new StaticText object.
			if (token.at(\type) === \number
				or: { token.at(\type) === \ratio }, {
				var oldKeyDownAction;
				// Add concatenated label.
				if (labelString.size > 0, {
					subView = StaticText.new(this).string_(labelString);
					lineHeight = max(lineHeight, subView.sizeHint.height);
					currentLayout.add(subView);
					labelString = "";
				});
				subView = TextField.new(this);
				subView.string = token.at(\string);
				subView.maxWidth = editCharacterWidth * max(token.at(\string).size, 3);
				subView.action = { | view |
					parameterValue.at(\tokens)[index].put(\string, view.string);
					action.value(this);
				};
				// Propagate all key events for when the ctrl key is held down by
				// returning false to those. They will be handled by parent view.
				oldKeyDownAction = subView.keyDownAction;
				subView.keyDownAction = { | view, char, modifiers, unicode, keycode |
					if (modifiers.isCtrl
						or: { char == $\t }, {
						// Fire off any edits on this view to update string.
						view.doAction;
						false;
					}, {
						oldKeyDownAction.value(view, char, modifiers, unicode, keycode);
					});
				};
				lineHeight = max(lineHeight, subView.sizeHint.height);
				currentLayout.add(subView);
			}, {
				// Scan all readonly tokens for line breaks, otherwise append
				// to current labelString.
				token.at(\string).do({ | c |
					if (c == $\n or: { c == $\r }, {
						if (labelString.size > 0, {
							subView = StaticText.new(this).string_(labelString);
							lineHeight = max(lineHeight, subView.sizeHint.height);
							currentLayout.add(subView);
							currentLayout.add(nil);
							labelString = "";
							this.layout.add(currentLayout);
							currentLayout = HLayout().spacing_(0).margins_(0);
							totalHeight = totalHeight + lineHeight + 2;
							lineHeight = 0;
						});
					}, {
						labelString = labelString ++ c;
					});
				});
			});
		});

		if (labelString.size > 0, {
			subView = StaticText.new(this).string_(labelString);
			lineHeight = max(lineHeight, subView.sizeHint.height);
			currentLayout.add(subView);
			labelString = "";
		});

		currentLayout.add(nil);

		totalHeight = totalHeight + lineHeight + 2;
		this.layout.add(currentLayout);
		this.fixedHeight = totalHeight
	}
}