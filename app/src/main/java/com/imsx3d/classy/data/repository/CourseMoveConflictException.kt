package com.imsx3d.classy.data.repository

class CourseMoveConflictException(val details: List<String>) : IllegalStateException("目标时间存在课程冲突")
