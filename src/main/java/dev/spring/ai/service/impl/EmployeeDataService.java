package dev.spring.ai.service.impl;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * In-memory mock service acting as a proxy for two external systems:
 *
 *  1. HRMS / Leave Management System  →  represented by {@link LeaveRecord}
 *  2. Project Allocation System        →  represented by {@link ProjectAllocation}
 *
 * In a real application these would be database queries or REST calls to third-party systems.
 * Here they are static in-memory lists so the focus stays on the AI tool-calling mechanics.
 */
@Service
public class EmployeeDataService
{
	/**
	 * Represents a single leave entry from the HRMS leave table.
	 *
	 * @param employeeName  full name of the employee
	 * @param leaveType     type of leave (e.g. "Annual Leave", "Festival Leave")
	 * @param startDate     leave start date in YYYY-MM-DD format
	 * @param endDate       leave end date in YYYY-MM-DD format
	 */
	public record LeaveRecord(
			String employeeName,
			String leaveType,
			String startDate,
			String endDate
	) {}

	/**
	 * Represents a single allocation entry from the project management / resource system.
	 *
	 * @param employeeName        full name of the employee
	 * @param role                their role on the project
	 * @param projectName         name of the project they are assigned to
	 * @param allocationPercent   percentage of their time allocated (0 = unallocated, 100 = fully booked)
	 * @param startDate           allocation start date in YYYY-MM-DD format (null if unallocated)
	 * @param endDate             allocation end date in YYYY-MM-DD format (null if unallocated)
	 */
	public record ProjectAllocation(
			String employeeName,
			String role,
			String projectName,
			int allocationPercent,
			String startDate,
			String endDate
	) {}

	/**
	 * Proxy for the HRMS leave table.
	 * Contains all approved leaves for the SolarVision team in April 2026.
	 */
	private static final List<LeaveRecord> LEAVE_RECORDS = List.of(
			new LeaveRecord("Ravi Shah",    "Festival Leave", "2026-04-05", "2026-04-07"),
			new LeaveRecord("Ankit Sharma", "Annual Leave",   "2026-04-10", "2026-04-15"),
			new LeaveRecord("Neha Patel",   "Annual Leave",   "2026-04-20", "2026-04-22")
			// Rahul Desai, Meena Raval, Arjun Mehta, and Sana Kapoor have no approved leave in April 2026
	);

	/**
	 * Proxy for the project allocation / resource management system.
	 * Shows current project commitments and release dates for each team member.
	 */
	private static final List<ProjectAllocation> PROJECT_ALLOCATIONS = List.of(
			new ProjectAllocation("Ravi Shah",    "Backend Lead Developer", "SolarVision Phase 1", 100, "2026-03-01", "2026-04-22"),
			new ProjectAllocation("Ankit Sharma", "Frontend Developer",     "SolarVision Phase 1", 100, "2026-03-01", "2026-04-22"),
			new ProjectAllocation("Rahul Desai",  "Data Engineer",          "SolarVision Phase 1", 100, "2026-03-01", "2026-04-08"),
			new ProjectAllocation("Neha Patel",   "UX Designer",            "SolarVision Phase 1",  50, "2026-03-01", "2026-05-06"),
			new ProjectAllocation("Meena Raval",  "QA Lead",                "Unallocated",           0,         null,         null),
			new ProjectAllocation("Arjun Mehta",  "Junior Backend Developer", "Internal Tools v2",  100, "2026-02-01", "2026-03-31"),
			new ProjectAllocation("Sana Kapoor",  "Frontend Developer",     "Unallocated",           0,         null,         null)
	);

	/**
	 * Returns all leave records — equivalent to a SELECT * FROM leave_table query.
	 *
	 * @return list of all approved leave entries across the team
	 */
	public List<LeaveRecord> getLeaveRecords()
	{
		return LEAVE_RECORDS;
	}

	/**
	 * Returns all project allocation records — equivalent to a SELECT * FROM allocation_table query.
	 *
	 * @return list of all project allocation entries across the team
	 */
	public List<ProjectAllocation> getProjectAllocations()
	{
		return PROJECT_ALLOCATIONS;
	}
}