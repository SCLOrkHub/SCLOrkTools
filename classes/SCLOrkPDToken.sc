SCLOrkPDToken {
	// \whiteSpace (tab, newlines, spaces)
	// \Pbindef
	// \blockComment
	// \lineComment
	// \PbindefName
	// \PbindefKey
	// \PbindefValue (meaning we don't know what to do here, just show code)
	// \PbindefValueFraction (eg: 120/124)
	// \PbindefValueNumber
	// \PbindefValuePseq
	// \PbindefValuePwhite
	// \PbindefValueBufNum (can show special UI here later to pick buffer)
	// \PbindefValuePrand
	var <>type;
	var <>lineStart;
	var <>columnStart;
	var <>lineEnd;
	var <>columnEnd;
	var <>stringStart;
	var <>stringEnd;

	// We take a larger string, keep that in-memory, and generate a bunch of tokens which
	// each have a string start and end in them, which are offsets into the larger string
	// where they live.
}