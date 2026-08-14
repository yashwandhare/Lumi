# R8 rules for release builds.
#
# Room and Hilt generate code that R8 handles correctly on its own via the
# consumer rules those libraries ship. Nothing extra is needed for them.
#
# Phase 1 will add keep rules for the LiteRT-LM native layer: it is reached over
# JNI, so R8 cannot see the call sites and will strip the classes without help.
# Add those rules in the same commit that wires the model harness, not before.
