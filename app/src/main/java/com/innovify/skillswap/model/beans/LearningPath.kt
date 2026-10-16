package com.innovify.skillswap.model.beans

// LearningPathResource del backend. status: "Active" | "Paused" | "Completed"
data class LearningPath(
    var id: Int,
    var studentId: Int,
    var goal: String,
    var goalSkillTags: List<String>?,
    var status: String,
    var nodes: List<PathNode>?,
    var advanced: Boolean
)

// PathNodeResource del backend. status: "Locked" | "Available" | "Completed"
data class PathNode(
    var id: Int,
    var skillTag: String,
    var skillName: String,
    var order: Int,
    var status: String,
    var prerequisiteSkillTags: List<String>?,
    var linkedCertificateId: Int?,
    var assessmentBlueprintId: Int?,
    var completedByCertificate: Boolean
)
