package com.olivia.core

/**
 * Small synthetic catalogue used by the offline replay. These are labels only: no real frame,
 * tattoo, person or image URI is embedded in the app or test report.
 */
public object MockVisualFixtures {
    public val centered: VisualFixtureManifest = fixture(
        id = "synthetic-centered",
        diagnoses = listOf(ok(DiagnosisKind.FRAMING), ok(DiagnosisKind.LIGHT), ok(DiagnosisKind.HORIZON)),
    )

    public val subjectRight: VisualFixtureManifest = fixture(
        id = "synthetic-subject-right",
        diagnoses = listOf(
            VisualDiagnosis(
                kind = DiagnosisKind.FRAMING,
                severity = Severity.MAJOR,
                confidence = Confidence(0.9f),
                direction = Direction.TURN_RIGHT,
            ),
        ),
    )

    public val handShadow: VisualFixtureManifest = fixture(
        id = "synthetic-hand-shadow",
        diagnoses = listOf(
            VisualDiagnosis(
                kind = DiagnosisKind.LIGHT,
                severity = Severity.MAJOR,
                confidence = Confidence(0.9f),
                direction = Direction.LEAVE_THE_SHADOW,
            ),
        ),
    )

    public val lowLight: VisualFixtureManifest = fixture(
        id = "synthetic-low-light-no-hand",
        diagnoses = listOf(
            VisualDiagnosis(
                kind = DiagnosisKind.LIGHT,
                severity = Severity.MINOR,
                confidence = Confidence(0.85f),
                direction = Direction.FACE_THE_LIGHT,
            ),
        ),
    )

    public val tiltedHorizon: VisualFixtureManifest = fixture(
        id = "synthetic-horizon-left",
        diagnoses = listOf(
            VisualDiagnosis(
                kind = DiagnosisKind.HORIZON,
                severity = Severity.MAJOR,
                confidence = Confidence(0.9f),
                direction = Direction.LEVEL_HORIZON_RIGHT,
            ),
        ),
    )

    public val targetAbsent: VisualFixtureManifest = fixture(
        id = "synthetic-target-absent",
        diagnoses = emptyList(),
    )

    private fun fixture(id: String, diagnoses: List<VisualDiagnosis>): VisualFixtureManifest =
        VisualFixtureManifest(
            id = id,
            source = "synthetic-vector-label",
            licensedForTesting = true,
            width = 720,
            height = 1_280,
            expectedDiagnoses = diagnoses,
        )

    private fun ok(kind: DiagnosisKind): VisualDiagnosis = VisualDiagnosis(
        kind = kind,
        severity = Severity.OK,
        confidence = Confidence(0.9f),
        direction = null,
    )
}
