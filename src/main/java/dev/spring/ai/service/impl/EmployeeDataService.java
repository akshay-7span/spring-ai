package dev.spring.ai.service.impl;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory mock for three external systems:
 *   1. HRMS / Leave Management System  → LeaveRecord
 *   2. Project Allocation System        → ProjectAllocation
 *   3. Employee Skills / HR Profile DB  → EmployeeProfile
 *
 * In a real application these would be database queries or REST calls to third-party systems.
 */
@Service
public class EmployeeDataService
{
	public record LeaveRecord(
			String employeeName,
			String leaveType,
			String startDate,
			String endDate
	) {}

	public record ProjectAllocation(
			String employeeName,
			String role,
			String projectName,
			int allocationPercent,
			String startDate,
			String endDate
	) {}

	public record EmployeeProfile(
			String employeeName,
			String role,
			String primaryTechnology,
			String skills,
			int yearsOfExperience
	) {}

	private static final List<LeaveRecord> LEAVE_RECORDS = List.of(
			new LeaveRecord("Ravi Shah",    "Festival Leave", "2026-04-05", "2026-04-07"),
			new LeaveRecord("Ankit Sharma", "Annual Leave",   "2026-04-10", "2026-04-15"),
			new LeaveRecord("Neha Patel",   "Annual Leave",   "2026-04-20", "2026-04-22"),
			new LeaveRecord("Arjun Mehta",  "Planned Leave",  "2026-04-20", "2026-04-22")
	);

	private static final List<ProjectAllocation> PROJECT_ALLOCATIONS = List.of(
			new ProjectAllocation("Ravi Shah",    "Backend Lead Developer",    "SolarVision Phase 1", 100, "2026-03-01", "2026-04-22"),
			new ProjectAllocation("Ankit Sharma", "Frontend Developer",        "SolarVision Phase 1", 100, "2026-03-01", "2026-04-22"),
			new ProjectAllocation("Rahul Desai",  "Data Engineer",             "SolarVision Phase 1", 100, "2026-03-01", "2026-04-08"),
			new ProjectAllocation("Neha Patel",   "UX Designer",               "SolarVision Phase 1",  50, "2026-03-01", "2026-05-06"),
			new ProjectAllocation("Meena Raval",  "QA Lead",                   "Unallocated",           0,         null,         null),
			new ProjectAllocation("Arjun Mehta",  "Junior Backend Developer",  "Internal Tools v2",   100, "2026-02-01", "2026-03-31"),
			new ProjectAllocation("Sana Kapoor",  "Frontend Developer",        "Unallocated",           0,         null,         null)
	);

	private static final Map<String, EmployeeProfile> EMPLOYEE_PROFILES = Map.of(
			"Ravi Shah",    new EmployeeProfile("Ravi Shah",    "Backend Lead Developer",   "Java",    "Java, Spring Boot, Microservices, REST APIs, PostgreSQL", 8),
			"Ankit Sharma", new EmployeeProfile("Ankit Sharma", "Frontend Developer",        "React",   "React, TypeScript, CSS, HTML, Redux", 5),
			"Rahul Desai",  new EmployeeProfile("Rahul Desai",  "Data Engineer",             "Python",  "Python, Apache Spark, SQL, Kafka, Airflow", 6),
			"Neha Patel",   new EmployeeProfile("Neha Patel",   "UX Designer",               "Figma",   "Figma, Sketch, User Research, Prototyping, Usability Testing", 4),
			"Meena Raval",  new EmployeeProfile("Meena Raval",  "QA Lead",                   "Selenium","Selenium, JUnit, TestNG, API Testing, Test Planning", 7),
			"Arjun Mehta",  new EmployeeProfile("Arjun Mehta",  "Junior Backend Developer",  "Java",    "Java, Spring Boot, REST APIs, MySQL, Git", 2),
			"Sana Kapoor",  new EmployeeProfile("Sana Kapoor",  "Frontend Developer",        "React",   "React, Vue.js, JavaScript, CSS, Tailwind", 3)
	);

	public List<LeaveRecord> getLeaveRecords()
	{
		return LEAVE_RECORDS;
	}

	public List<ProjectAllocation> getProjectAllocations()
	{
		return PROJECT_ALLOCATIONS;
	}

	public Optional<EmployeeProfile> getEmployeeProfile(String employeeName)
	{
		return Optional.ofNullable(EMPLOYEE_PROFILES.get(employeeName));
	}
}
