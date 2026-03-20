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

/**
 * Spring AI tool definitions that expose employee data to the LLM.
 *
 * Each method annotated with {@link Tool} is registered with the LLM as a callable function.
 * The LLM decides which tools to call, and in what order, based on what information it needs.
 * All tools return raw data with no pre-filtering or interpretation — the LLM does all reasoning.
 *
 * Tool interaction pattern (agentic loop):
 *
 *   Iteration 1: LLM calls getTeamProjectAllocations()
 *                 → discovers who is free and when
 *   Iteration 2: LLM calls getEmployeeSkillProfile() for each available candidate
 *                 → discovers their technical skills; narrows down the shortlist
 *   Iteration 3: LLM calls getTeamLeaveRecords()
 *                 → cross-checks shortlisted candidates for leave conflicts
 *   Iteration 4: LLM calls draftResourceRecommendation() with the final candidate's details
 *                 → this is the ACTION tool; it can only be called once all read tools
 *                   have been executed because its parameters (name, availableFrom,
 *                   matchedSkills, leaveConflicts) come directly from the read results.
 *                   This is the forcing function that makes the loop genuinely sequential.
 *   Final:        LLM receives confirmation and produces its summary answer.
 */
@Component
public class EmployeeTools
{
	private static final Logger log = LoggerFactory.getLogger(EmployeeTools.class);

	private final EmployeeDataService employeeDataService;

	public EmployeeTools(EmployeeDataService employeeDataService)
	{
		this.employeeDataService = employeeDataService;
	}

	/**
	 * Tool — proxy for the HRMS leave management system.
	 *
	 * Returns all approved employee leave records so the LLM can determine
	 * how many days each team member will be absent and during which dates.
	 *
	 * @return raw leave records for the entire team
	 */
	@Tool(description = """
			Fetches all approved employee leave records from the HR system.
			Returns each record with employee name, leave type, start date, and end date.
			Use this to find out which team members are on leave and for how long during a given period.
			""")
	public List<LeaveRecord> getTeamLeaveRecords()
	{
		log.info("----------------------------------------------------------------");
		log.info("TOOL CALL : LLM requested → getTeamLeaveRecords()");
		log.info("           Source  : HRMS leave management system (proxy)");
		log.info("           Action  : Fetching all approved employee leave records");

		List<LeaveRecord> records = employeeDataService.getLeaveRecords();

		log.info("           Result  : {} leave record(s) found", records.size());
		records.forEach(r -> log.info("             → {} | {} | {} to {}", r.employeeName(), r.leaveType(), r.startDate(), r.endDate()));
		log.info("           Sending leave data back to LLM...");
		log.info("----------------------------------------------------------------");

		return records;
	}

	/**
	 * Tool — proxy for the project allocation / resource management system.
	 *
	 * Returns all project allocation entries so the LLM can determine who is
	 * currently committed to a project, at what capacity, and when they are released.
	 *
	 * @return raw project allocation records for the entire team
	 */
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
		log.info("           Source  : Resource management system (proxy)");
		log.info("           Action  : Fetching all employee project allocation records");

		List<ProjectAllocation> allocations = employeeDataService.getProjectAllocations();

		log.info("           Result  : {} allocation record(s) found", allocations.size());
		allocations.forEach(a -> log.info("             → {} | {} | {}% on {} | {} to {}",
				a.employeeName(), a.role(), a.allocationPercent(), a.projectName(), a.startDate(), a.endDate()));
		log.info("           Sending allocation data back to LLM...");
		log.info("----------------------------------------------------------------");

		return allocations;
	}

	/**
	 * Tool — proxy for the HR employee skill / profile database.
	 *
	 * This is a PARAMETERISED tool: the LLM passes the name of a specific employee
	 * it wants to know about.  This is the key difference from the two bulk tools above —
	 * the LLM can only call this after it has already identified candidate names from
	 * getTeamProjectAllocations().  That sequential dependency is what creates the
	 * agentic loop: the output of one iteration determines the input of the next.
	 *
	 * @param employeeName the full name of the employee whose profile is requested
	 * @return the skill profile, or a not-found message if the name is unknown
	 */
	@Tool(description = """
			Fetches the skill profile for a single employee from the HR database.
			Returns their role, primary technology, full list of technical skills, and years of experience.
			Pass the employee's full name as the parameter.
			""")
	public String getEmployeeSkillProfile(String employeeName)
	{
		log.info("----------------------------------------------------------------");
		log.info("TOOL CALL : LLM requested → getEmployeeSkillProfile(\"{}\")", employeeName);
		log.info("           Source  : HR employee skill database (proxy)");
		log.info("           Action  : Fetching skill profile for employee : {}", employeeName);

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
		log.info("           Sending skill profile back to LLM...");
		log.info("----------------------------------------------------------------");

		return result;
	}

	/**
	 * Tool — ACTION tool that records the final resource recommendation.
	 *
	 * This is the key forcing function of the agentic loop.  Unlike the three READ tools
	 * above, this tool requires the LLM to supply specific data it could only have obtained
	 * by calling the read tools first:
	 *
	 *   employeeName   → only known after calling getTeamProjectAllocations
	 *   availableFrom  → only known after calling getTeamProjectAllocations
	 *   matchedSkills  → only known after calling getEmployeeSkillProfile
	 *   leaveConflicts → only known after calling getTeamLeaveRecords
	 *
	 * Because all four parameters depend on prior read results, the LLM cannot call
	 * this tool in isolation or batch it with the reads in a single iteration.
	 * It is forced to gather all data first — that is what makes this a genuine agentic loop.
	 *
	 * @param employeeName   full name of the recommended employee
	 * @param availableFrom  date from which the employee is free (YYYY-MM-DD or "immediately")
	 * @param matchedSkills  the skills that match the project requirement
	 * @param leaveConflicts summary of any leave conflicts, or "None" if clear
	 * @return confirmation string that the recommendation has been recorded
	 */
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
		log.info("           Action         : Recording final resource recommendation");
		log.info("           Employee       : {}", employeeName);
		log.info("           Available From : {}", availableFrom);
		log.info("           Matched Skills : {}", matchedSkills);
		log.info("           Leave Conflicts: {}", leaveConflicts);
		log.info("           ✓ Recommendation recorded — returning confirmation to LLM");
		log.info("----------------------------------------------------------------");

		return String.format(
				"Recommendation recorded: %s is available from %s with skills [%s]. Leave conflicts: %s.",
				employeeName, availableFrom, matchedSkills, leaveConflicts
		);
	}
}