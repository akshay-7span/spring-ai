package dev.spring.ai.service.impl;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory mock service acting as a proxy for three external systems:
 *
 *  1. HRMS / Leave Management System  →  represented by {@link LeaveRecord}
 *  2. Project Allocation System        →  represented by {@link ProjectAllocation}
 *  3. Employee Skills / HR Profile DB  →  represented by {@link EmployeeProfile}
 *
 * In a real application these would be database queries or REST calls to third-party systems.
 * Here they are static in-memory structures so the focus stays on the AI tool-calling mechanics.
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
	 * Represents a skill profile entry from the HR employee database.
	 *
	 * @param employeeName        full name of the employee
	 * @param role                their job title / role
	 * @param primaryTechnology   the main technology or domain they specialise in
	 * @param skills              comma-separated list of technical skills
	 * @param yearsOfExperience   total years of professional experience
	 */
	public record EmployeeProfile(
			String employeeName,
			String role,
			String primaryTechnology,
			String skills,
			int yearsOfExperience
	) {}

	/**
	 * Proxy for the HRMS leave table.
	 * Contains all approved leaves for the SolarVision team in April 2026.
	 */
	private static final List<LeaveRecord> LEAVE_RECORDS = List.of(
			new LeaveRecord("Ravi Shah",    "Festival Leave", "2026-04-05", "2026-04-07"),
			new LeaveRecord("Ankit Sharma", "Annual Leave",   "2026-04-10", "2026-04-15"),
			new LeaveRecord("Neha Patel",   "Annual Leave",   "2026-04-20", "2026-04-22"),
			new LeaveRecord("Arjun Mehta",   "Planned Leave",   "2026-04-20", "2026-04-22")
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
	 * Proxy for the HR employee skill / profile database.
	 * Keyed by employee name for O(1) lookup — simulates a SELECT WHERE name = ? query.
	 */
	private static final Map<String, EmployeeProfile> EMPLOYEE_PROFILES = Map.of(
			"Ravi Shah",    new EmployeeProfile("Ravi Shah",    "Backend Lead Developer",    "Java",   "Java, Spring Boot, Microservices, REST APIs, PostgreSQL", 8),
			"Ankit Sharma", new EmployeeProfile("Ankit Sharma", "Frontend Developer",         "React",  "React, TypeScript, CSS, HTML, Redux", 5),
			"Rahul Desai",  new EmployeeProfile("Rahul Desai",  "Data Engineer",              "Python", "Python, Apache Spark, SQL, Kafka, Airflow", 6),
			"Neha Patel",   new EmployeeProfile("Neha Patel",   "UX Designer",                "Figma",  "Figma, Sketch, User Research, Prototyping, Usability Testing", 4),
			"Meena Raval",  new EmployeeProfile("Meena Raval",  "QA Lead",                    "Selenium","Selenium, JUnit, TestNG, API Testing, Test Planning", 7),
			"Arjun Mehta",  new EmployeeProfile("Arjun Mehta",  "Junior Backend Developer",   "Java",   "Java, Spring Boot, REST APIs, MySQL, Git", 2),
			"Sana Kapoor",  new EmployeeProfile("Sana Kapoor",  "Frontend Developer",         "React",  "React, Vue.js, JavaScript, CSS, Tailwind", 3)
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

	/**
	 * Returns the skill profile for a single employee by name.
	 * Equivalent to SELECT * FROM employee_profiles WHERE name = ?
	 *
	 * @param employeeName the full name of the employee to look up
	 * @return an Optional containing the profile, or empty if not found
	 */
	public Optional<EmployeeProfile> getEmployeeProfile(String employeeName)
	{
		return Optional.ofNullable(EMPLOYEE_PROFILES.get(employeeName));
	}
}