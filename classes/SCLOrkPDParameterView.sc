SCLOrkPDParameterView : View {
	var <name;
	var <value;

	*new { | parent, parameterName, parameterValue |
		^super.new(parent).init(parameterName, parameterValue);
	}

	init { | parameterName, parameterValue |
		var subView, currentLayout;

		name = parameterName;
		value = parameterValue;
		this.layout = VLayout();
		currentLayout = HLayout();

		// Add label of parameter name.
		subView = StaticText.new(this);
		subView.string = parameterName.asString ++ ":";
		subView.font = Font(Font.defaultSansFace, bold: true);
		currentLayout.add(subView);

		// NumberBox
		// RangeSlider
		// ScopeView
		// Slider
		// SoundFileView

		parameterValue.at(\tokens).do({ | token, index |
			var lineBreak = false;
			if (token.at(\type) === \number, {
				subView = TextField.new(this);
				subView.string = token.at(\string);
			}, {
				// Scan comments and whitespace for newlines
				var labelString = token.at(\string);
				if (token.at(\type) === \lineComment
					or: { token.at(\type) === \whitespace
						and: { labelString.contains("\n") }}, {
						var filterString = "";
						labelString.do({ | c |
							if (c != $\n, {
								filterString = filterString ++ c;
							});
						});
						labelString = filterString;
						lineBreak = true;
				});

				subView = StaticText.new(this);
				subView.string = labelString;
			});
			currentLayout.add(subView);
			if (lineBreak, {
				this.layout.add(currentLayout);
				currentLayout = HLayout();
			});
		});
		currentLayout.add(nil);
		this.layout.add(currentLayout);
	}


}