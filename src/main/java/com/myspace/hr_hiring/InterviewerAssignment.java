package com.myspace.hr_hiring;

/**
 * This class was automatically generated by the data modeler tool.
 */

public class InterviewerAssignment implements java.io.Serializable {

	static final long serialVersionUID = 1L;

	private java.lang.String interviewerName;
	private java.lang.String comment;

	public InterviewerAssignment() {
	}

	public java.lang.String getInterviewerName() {
		return this.interviewerName;
	}

	public void setInterviewerName(java.lang.String interviewerName) {
		this.interviewerName = interviewerName;
	}

	public java.lang.String getComment() {
		return this.comment;
	}

	public void setComment(java.lang.String comment) {
		this.comment = comment;
	}

	public InterviewerAssignment(java.lang.String interviewerName,
			java.lang.String comment) {
		this.interviewerName = interviewerName;
		this.comment = comment;
	}

}