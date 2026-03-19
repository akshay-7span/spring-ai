package dev.spring.ai.tools;

import dev.spring.ai.service.impl.EmployeeDataService;
import dev.spring.ai.service.impl.EmployeeDataService.LeaveRecord;
import dev.spring.ai.service.impl.EmployeeDataService.ProjectAllocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Spring AI tool definitions that expose employee data to the LLM.
 *
 * Each method annotated with {@link Tool} can be invoked by the LLM during a chat session.
 * The LLM decides when to call these tools based on the context in the prompt.
 * Spring AI handles the round-trip automatically:
 *
 *   LLM reads prompt
 *     → decides it needs leave / allocation data
 *     → Spring AI invokes the tool method          ← logs fire HERE, between the two LLM calls
 *     → raw result is sent back to the LLM
 *     → LLM reasons over the data and produces its final answer
 *
 * Both tools return raw data with no pre-filtering or interpretation — the LLM does all reasoning.
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
}