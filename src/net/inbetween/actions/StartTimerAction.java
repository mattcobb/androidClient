package net.inbetween.actions;

import net.inbetween.Action;
import net.inbetween.ActionType;

public class StartTimerAction extends Action
{
   long seconds;
   private String name;
   
   public StartTimerAction()
   {
      super();
      type = ActionType.ACTION_START_TIMER; 
   }

   public StartTimerAction(String inName, long inSecs)
   {
      super();
      type = ActionType.ACTION_START_TIMER;
      seconds = inSecs;
      name = inName;
   }
   
   public void setSeconds(long inSecs)
   {
      seconds = inSecs;
   }
   
   public long getSeconds()
   {
      return seconds;
   }

   public String getName()
   {
      return name;
   }

   public void setName(String name)
   {
      this.name = name;
   }
}
