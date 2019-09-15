package com.myspace.hr_hiring;

/**
 * This class was automatically generated by the data modeler tool.
 */

public class InterviewAppointment implements java.io.Serializable {

	static final long serialVersionUID = 1L;

	private java.lang.String interviewer;
	private String interviewStarts;
	private java.lang.Integer interviewDurationMinutes;
	private java.lang.String comment;

	private java.lang.String interviewee;

	public InterviewAppointment() {
	}

	public java.lang.String getInterviewer() {
		return this.interviewer;
	}

	public void setInterviewer(java.lang.String interviewer) {
		this.interviewer = interviewer;
	}

	public java.lang.Integer getInterviewDurationMinutes() {
		return this.interviewDurationMinutes;
	}

	public void setInterviewDurationMinutes(
			java.lang.Integer interviewDurationMinutes) {
		this.interviewDurationMinutes = interviewDurationMinutes;
	}

	public java.lang.String getComment() {
		return this.comment;
	}

	public void setComment(java.lang.String comment) {
		this.comment = comment;
	}

	public java.lang.String getInterviewee() {
		return this.interviewee;
	}

	public void setInterviewee(java.lang.String interviewee) {
		this.interviewee = interviewee;
	}

	public java.lang.String getInterviewStarts() {
		return this.interviewStarts;
	}

	public void setInterviewStarts(java.lang.String interviewStarts) {
		this.interviewStarts = interviewStarts;
	}

	public InterviewAppointment(java.lang.String interviewer,
			java.lang.String interviewStarts,
			java.lang.Integer interviewDurationMinutes,
			java.lang.String comment, java.lang.String interviewee) {
		this.interviewer = interviewer;
		this.interviewStarts = interviewStarts;
		this.interviewDurationMinutes = interviewDurationMinutes;
		this.comment = comment;
		this.interviewee = interviewee;
	}

}