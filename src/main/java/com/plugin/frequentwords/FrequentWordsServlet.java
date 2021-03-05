package com.plugin.frequentwords;

import static java.util.Comparator.reverseOrder;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.search.SearchException;
import com.atlassian.jira.issue.search.SearchResults;
import com.atlassian.jira.jql.builder.JqlClauseBuilder;
import com.atlassian.jira.jql.builder.JqlQueryBuilder;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.security.Permissions;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.UserProjectHistoryManager;
import com.atlassian.jira.web.bean.PagerFilter;
import com.atlassian.plugin.spring.scanner.annotation.imports.JiraImport;
import com.atlassian.query.Query;
import com.atlassian.velocity.VelocityManager;
import com.google.common.collect.Lists;

public class FrequentWordsServlet extends HttpServlet{
    
    private static final long serialVersionUID = 1L;
    
    public static final int WORD_LIMIT = 100;
    
    @JiraImport
    private VelocityManager velocityManager;
    
    public FrequentWordsServlet(VelocityManager velocityManager) {
        this.velocityManager = velocityManager;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/html");
        List<Issue> issues = getIssues();
        List<String> frequentWords = findFrequentWords(issues);
        for (String word : frequentWords) {
            resp.getWriter().write((word + "<br/>"));
        }
        resp.getWriter().close();
    }
    
    private List<Issue> getIssues() {
        JiraAuthenticationContext authenticationContext = ComponentAccessor.getJiraAuthenticationContext();
        ApplicationUser user = authenticationContext.getLoggedInUser();
        JqlClauseBuilder jqlClauseBuilder = JqlQueryBuilder.newClauseBuilder();
        SearchService searchService = ComponentAccessor.getComponentOfType(SearchService.class);
        UserProjectHistoryManager historyManager = ComponentAccessor.getComponentOfType(UserProjectHistoryManager.class);
        Project project = historyManager.getCurrentProject(Permissions.PROJECT_ADMIN, user);
        Query query = jqlClauseBuilder.project(project.getName()).buildQuery();
        PagerFilter pagerFilter = PagerFilter.getUnlimitedFilter();
        
        SearchResults searchResults = null;
        try {
            searchResults = searchService.search(user, query, pagerFilter);
        } catch (SearchException e) {
            e.printStackTrace();
        }
        return searchResults != null ? searchResults.getIssues() : null;
    }

    private List<String> findFrequentWords(List<Issue> issues) {
        List<String> issuesArray = Lists.newArrayList();

        issues.forEach(issue -> {
            issuesArray.addAll(Arrays.asList(issue.getSummary().split("\\W+")));
            issuesArray.addAll(Arrays.asList(issue.getDescription().split("\\W+")));
        });

        List<String> result = issuesArray
                .stream().collect(groupingBy(identity(), counting()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(reverseOrder())
                .thenComparing(Map.Entry.comparingByKey()))
                .limit(WORD_LIMIT).map(Map.Entry::getKey)
                .collect(toList());

        return result;
    }

}
