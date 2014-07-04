/**********************************************************************
 *
 * Copyright (c) by Olaf Willuhn
 * All rights reserved
 *
 **********************************************************************/

package de.willuhn.jameica.redmine.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.taskadapter.redmineapi.bean.Issue;
import com.taskadapter.redmineapi.bean.Project;
import com.taskadapter.redmineapi.bean.TimeEntryActivity;

import de.willuhn.annotation.Lifecycle;
import de.willuhn.annotation.Lifecycle.Type;
import de.willuhn.jameica.messaging.StatusBarMessage;
import de.willuhn.jameica.redmine.beans.ProjectTree;
import de.willuhn.jameica.system.Application;
import de.willuhn.logging.Logger;
import de.willuhn.util.ApplicationException;

/**
 * Redmine-Service, der die Ergebnisse fuer schnelleren Zugriff cached.
 */
@Lifecycle(Type.CONTEXT)
public class CachingRedmineService extends AbstractRedmineService
{
  private Object lock = new Object();
  private List<ProjectTree> projects = null;
  private List<TimeEntryActivity> activities = null;
  private Map<Integer,List<Issue>> issues = new HashMap<Integer,List<Issue>>();
  
  /**
   * @see de.willuhn.jameica.redmine.service.AbstractRedmineService#getProjects()
   */
  @Override
  public List<ProjectTree> getProjects() throws ApplicationException
  {
    synchronized (lock)
    {
      if (this.projects == null)
        this.projects = super.getProjects();
      
      return this.projects;
    }
  }
  
  /**
   * @see de.willuhn.jameica.redmine.service.AbstractRedmineService#getIssues(com.taskadapter.redmineapi.bean.Project)
   */
  @Override
  public List<Issue> getIssues(Project project) throws ApplicationException
  {
    synchronized (lock)
    {
      List<Issue> list = this.issues.get(project.getId());
      if (list == null)
      {
        list = super.getIssues(project);
        this.issues.put(project.getId(),list);
      }
      return list;
    }
  }
  
  /**
   * @see de.willuhn.jameica.redmine.service.AbstractRedmineService#getActivities()
   */
  @Override
  public List<TimeEntryActivity> getActivities() throws ApplicationException
  {
    synchronized (lock)
    {
      if (this.activities == null)
        this.activities = super.getActivities();
      
      return this.activities;
    }
  }
  
  /**
   * @see de.willuhn.jameica.redmine.service.AbstractRedmineService#reconnect()
   */
  @Override
  public void reconnect()
  {
    super.reconnect();

    synchronized (lock)
    {
      long started = System.currentTimeMillis();
      Logger.info("refreshing cache");
      
      this.projects = null;
      this.issues.clear();
      
      try
      {
        this.getActivities();
        this.projects = this.getProjects();
        for (ProjectTree p:this.projects)
        {
          this.reloadIssues(p);
        }
        
        Logger.info("refreshing finished, took " + ((System.currentTimeMillis() - started) / 1000) + " seconds");
      }
      catch (ApplicationException ae)
      {
        Application.getMessagingFactory().sendMessage(new StatusBarMessage(ae.getMessage(),StatusBarMessage.TYPE_ERROR));
      }
    }
  }
  
  /**
   * Fuehrt ein rekursives Reload der Issues in dem Project-Baum durch.
   * @param p der Projekt-Baum.
   * @throws ApplicationException
   */
  private void reloadIssues(ProjectTree p) throws ApplicationException
  {
    synchronized (lock)
    {
      // Das Projekt selbst
      this.getIssues(p.getProject());
      
      // Jetzt die Kinder
      for (ProjectTree child:p.getChildren())
      {
        this.reloadIssues(child);
      }
    }
  }
}


