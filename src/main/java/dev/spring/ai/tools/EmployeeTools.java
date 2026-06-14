package dev.spring.ai.tools;

import dev.spring.ai.service.impl.EmployeeDataService;
import dev.spring.ai.service.impl.EmployeeDataService.EmployeeProfile;
import dev.spring.ai.service.impl.EmployeeDataService.LeaveRecord;
import dev.spring.ai.service.impl.EmployeeDataService.ProjectAllocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class EmployeeTools
{
	private static final Logger log = LoggerFactory.getLogger(EmployeeTools.class);

	private final EmployeeDataService employeeDataService;

	public EmployeeTools(EmployeeDataService employeeDataService)
	{
		this.employeeDataService = employeeDataService;
	}

	@Tool(description = """
			Fetches all approved employee leave records from the HR system.
			Returns each record with employee name, leave type, start date, and end date.
			Use this to find out which team members are on leave and for how long during a given period.
			""")
	public List<LeaveRecord> getTeamLeaveRecords()
	{
		log.info("----------------------------------------------------------------");
		log.info("TOOL CALL : LLM requested → getTeamLeaveRecords()");

		List<LeaveRecord> records = employeeDataService.getLeaveRecords();

		log.info("           Result  : {} leave record(s) found", records.size());
		records.forEach(r -> log.info("             → {} | {} | {} to {}", r.employeeName(), r.leaveType(), r.startDate(), r.endDate()));
		log.info("----------------------------------------------------------------");

		return records;
	}

	@Tool(description = """
			Fetches all employee project allocation records from the resource management system.
			Returns each record with employee name, role, project name, allocation percentage (0 = unallocated, 100 = fully booked),
			and the start and end dates of the allocation.
			Use this to find out who is available, partially available, or fully committed during a given period.
			""")
	public List<ProjectAllocation> getTeamProjectAllocations()
	{
		log.info("----------------------------------------------------------------");
		log.info("TOOL CALL : LLM requested → getTeamProjectAllocations()");

		List<ProjectAllocation> allocations = employeeDataService.getProjectAllocations();

		log.info("           Result  : {} allocation record(s) found", allocations.size());
		allocations.forEach(a -> log.info("             → {} | {} | {}% on {} | {} to {}",
				a.employeeName(), a.role(), a.allocationPercent(), a.projectName(), a.startDate(), a.endDate()));
		log.info("----------------------------------------------------------------");

		return allocations;
	}

	// Parameterised tool — the LLM can only call this after it knows which employee names to look up,
	// which it learns from getTeamProjectAllocations(). That data dependency forces sequential iterations.
	@Tool(description = """
			Fetches the skill profile for a single employee from the HR database.
			Returns their role, primary technology, full list of technical skills, and years of experience.
			Pass the employee's full name as the parameter.
			""")
	public String getEmployeeSkillProfile(String employeeName)
	{
		log.info("----------------------------------------------------------------");
		log.info("TOOL CALL : LLM requested → getEmployeeSkillProfile(\"{}\")", employeeName);

		Optional<EmployeeProfile> profile = employeeDataService.getEmployeeProfile(employeeName);

		if (profile.isEmpty())
		{
			log.info("           Result  : No profile found for '{}'", employeeName);
			log.info("----------------------------------------------------------------");
			return "No profile found for employee: " + employeeName;
		}

		EmployeeProfile p = profile.get();
		String result = String.format(
				"Name: %s | Role: %s | Primary Technology: %s | Skills: %s | Experience: %d years",
				p.employeeName(), p.role(), p.primaryTechnology(), p.skills(), p.yearsOfExperience()
		);

		log.info("           Result  : {}", result);
		log.info("----------------------------------------------------------------");

		return result;
	}

	// ACTION tool — all four parameters come from prior read tool results.
	// The LLM cannot call this until it has run the three read tools above,
	// making the loop genuinely sequential rather than a single batch of parallel calls.
	@Tool(description = """
			Records a resource recommendation for a project assignment.
			Takes the recommended employee's name, the date they are available from,
			the skills that match the project requirement, and any leave conflicts during the period.
			Returns a confirmation that the recommendation has been recorded.
			""")
	public String draftResourceRecommendation(String employeeName,
	                                           String availableFrom,
	                                           String matchedSkills,
	                                           String leaveConflicts)
	{
		log.info("----------------------------------------------------------------");
		log.info("TOOL CALL : LLM requested → draftResourceRecommendation()");
		log.info("           Employee       : {}", employeeName);
		log.info("           Available From : {}", availableFrom);
		log.info("           Matched Skills : {}", matchedSkills);
		log.info("           Leave Conflicts: {}", leaveConflicts);
		log.info("----------------------------------------------------------------");

		return String.format(
				"Recommendation recorded: %s is available from %s with skills [%s]. Leave conflicts: %s.",
				employeeName, availableFrom, matchedSkills, leaveConflicts
		);
	}
}
