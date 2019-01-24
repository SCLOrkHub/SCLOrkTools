SCLOrkPDParameterView : View {
	const editCharacterWidth = 16;
	var <name;
	var <value;

	*new { | parent, parameterName, parameterValue |
		^super.new(parent).init(parameterName, parameterValue);
	}

	init { | parameterName, parameterValue |
		var subView, currentLayout, labelString;

		name = parameterName;
		value = parameterValue;
		this.layout = VLayout();
		currentLayout = HLayout();

		// Add label of parameter name.
		subView = StaticText.new(this);
		subView.string = parameterName.asString ++ ":";
		subView.font = Font(Font.defaultSansFace, bold: true);
		currentLayout.add(subView);
		labelString = "";

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
			if (token.at(\type) === \number, {
				var oldKeyDownAction;
				// Add concatenated label.
				if (labelString.size > 0, {
					currentLayout.add(StaticText.new(this).string_(
						labelString));
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
					if (modifiers.isCtrl, {
						// Fire off any edits on this view to update string.
						view.doAction;
						false;
					}, {
						oldKeyDownAction.value(view, char, modifiers, unicode, keycode);
					});
				};
				currentLayout.add(subView);
			}, {
				// Scan all readonly tokens for line breaks, otherwise append
				// to current labelString.
				token.at(\string).do({ | c |
					if (c == $\n or: { c == $\r }, {
						if (labelString.size > 0, {
							currentLayout.add(StaticText.new(this).string_(
								labelString));
							currentLayout.add(nil);
							labelString = "";
							this.layout.add(currentLayout);
							currentLayout = HLayout();
						});
					}, {
						labelString = labelString ++ c;
					});
				});
			});
		});

		if (labelString.size > 0, {
			currentLayout.add(StaticText.new(this).string_(labelString));
			labelString = "";
		});
		currentLayout.add(nil);
		this.layout.add(currentLayout);
	}
}