package sample.agent;

import java.awt.geom.Area;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;


import rescuecore2.log.Logger;
import rescuecore2.messages.Command;
import rescuecore2.standard.entities.AmbulanceTeam;
import rescuecore2.standard.entities.Blockade;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.Civilian;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.Refuge;
import rescuecore2.standard.entities.Road;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.ChangeSet;
import rescuecore2.worldmodel.EntityID;
import sample.agent.SampleAgent;
import sample.message.MessagePriority;
import sample.message.Type.AgentIsBuriedMessage;
import sample.message.Type.AgentIsStuckMessage;
import sample.message.Type.BuildingIsExploredMessage;
import sample.message.Type.CivilianInformationMessage;
import sample.message.Type.CivilianIsSavedOrDeadMessage;
import sample.message.Type.Message;
import sample.message.Type.MessageCount;
import sample.message.Type.MessageType;
import sample.utilities.DistanceSorter;
import sample.utilities.Path;
import sample.utilities.Search.PathType;

import holyshit.target.ATTarget;
/**
 * SEU's Ambulance Team Agent.
 */
public class SampleAmbulanceTeam extends SampleAgent<AmbulanceTeam> {
	//private EntityID searchTarget = null;
	private ArrayList<ATTarget> targets = new ArrayList<ATTarget>(); 
	//任务列表,限制 1（当前） + 10（备选）个任务
	private StandardEntity lastPosition = null; // 上次的位置

	public SampleAmbulanceTeam() {
		super();
	}

	@Override
	public String toString() {
		return "SEU Ambulance Team #" + getNo();
	}

	@Override
	protected void postConnect() {
		super.postConnect();
		new HashSet<EntityID>();

	}

	@Override
	protected EnumSet<StandardEntityURN> getRequestedEntityURNsEnum() {
		return EnumSet.of(StandardEntityURN.AMBULANCE_TEAM);
	}

	protected void processMessage(MessageCount message) {
		int counter = message.getCounter();
		MessageCount reply = new MessageCount(counter + 1);
		sendMessage(reply, MessagePriority.High);
	}

	@Override
	protected void thinkAndAct() {
	}

	protected void printKnownCivilians() {
		for (StandardEntity next : worldmodel.getHumans()) {
			if (next instanceof Civilian) {
				Civilian civilian = (Civilian) next;
				EntityID id = civilian.getPosition();
				StandardEntity position = worldmodel.getEntity(id);
				if (position instanceof Building) {
				}
			}
		}
	}

	@Override
	protected void processMessage(Message message) {
		if (message instanceof BuildingIsExploredMessage) {
			BuildingIsExploredMessage explorationMessage = (BuildingIsExploredMessage) message;
			processExplorationMessage(explorationMessage);
		}
		if (message instanceof CivilianInformationMessage) {
			CivilianInformationMessage civilianInformationMessage = (CivilianInformationMessage) message;
			processCivilianInformationMessage(civilianInformationMessage);
		}
		if (message instanceof CivilianIsSavedOrDeadMessage) {
			CivilianIsSavedOrDeadMessage civilianIsSavedMessage = (CivilianIsSavedOrDeadMessage) message;
			processCivilianIsSavedMessage(civilianIsSavedMessage);
		}
		if (message instanceof AgentIsBuriedMessage) {
			AgentIsBuriedMessage agentIsBuriedMessage = (AgentIsBuriedMessage) message;
			processAgentIsBuriedMessage(agentIsBuriedMessage);
		}

	}

	protected void processCivilianInformationMessage(
			CivilianInformationMessage message) {
		EntityID location = new EntityID(message.getLocationId());
		//StandardEntity civilianLocation = worldmodel.getEntity(location);
		Civilian civilian = new Civilian(new EntityID(message.getCivilianId()));
		civilian.setHP(message.getHp());
		civilian.setDamage(message.getDamage());
		civilian.setBuriedness(message.getBuriedness());
		if(null != civilian)
		{
			int TIME_TOTAL = 300; //总游戏时间
			int deathTime = getTimeStep()
					 + civilian.getHP()/(1+civilian.getDamage()); //第几秒死掉
			int liveTime = TIME_TOTAL - deathTime;
			int dis = worldmodel.getDistance(me().getID(), location); //距离被困者的距离
			//StandardEntityURN myURN = agent.getStandardURN(); //身份和地位。。
			//TODO: 再高端一点，算回程时间以及救援到达时间。。。以后再说吧
			if( deathTime< TIME_TOTAL )
			{
				//有的活。。
				//TODO: 问一下智能体死亡扣分的情况
				//当前计算公式
				// 权重 = 身份指数 (:公务员2   平民1，跟智能体死亡后扣分相同)
				//         * livetime
				//         / ( 1+dis )       ; 1是为了与当前救援人员比较而不会除以0
				int reputation = 1;
				int weight = reputation* liveTime/ (1 + dis);
				ATTarget myTarget = new ATTarget(weight
						, location
						, new EntityID(message.getCivilianId()));
				targets.add(myTarget);
				eliminateTargets();
				
			}
		}
		

	}

	protected void processAgentIsBuriedMessage(AgentIsBuriedMessage message) {
		/**
		 * @author iorange
		 * AT处理Agent被埋消息，增加到任务列表中，限定为10个任务（包含当前为11个任务）
		 */
		int TIME_TOTAL = 300; //总游戏时间
		EntityID location = new EntityID(message.getLocationId());
		//StandardEntity agentLocation = worldmodel.getEntity(location);//被埋的位置
		Human agent;
		agent = worldmodel.getEntity(new EntityID(message.getAgentId()),
				Human.class);//被埋的人
		if (agent != null) {
			agent.setHP(message.getHp());
			agent.setDamage(message.getDamage());
			agent.setBuriedness(message.getBuriedness());
			int deathTime = getTimeStep()
					 + agent.getHP()/(1+agent.getDamage()); //第几秒死掉
			int liveTime = TIME_TOTAL - deathTime;
			int dis = worldmodel.getDistance(me().getID(), location); //距离被困者的距离
			//StandardEntityURN myURN = agent.getStandardURN(); //身份和地位。。
			//TODO: 再高端一点，算回程时间以及救援到达时间。。。以后再说吧
			if( deathTime< TIME_TOTAL )
			{
				//有的活。。
				//TODO: 问一下智能体死亡扣分的情况
				//当前计算公式
				// 权重 = 身份指数 (:公务员2   平民1，跟智能体死亡后扣分相同)
				//         * livetime
				//         / ( 1+dis )       ; 1是为了与当前救援人员比较而不会除以0
				int reputation = 2;
				int weight = reputation* liveTime/ (1 + dis);
				ATTarget myTarget = new ATTarget(weight
						, location
						, new EntityID(message.getAgentId()));
				targets.add(myTarget);
				eliminateTargets();
				
			}
			
			
		}
	}

	public void processCivilianIsSavedMessage(
			CivilianIsSavedOrDeadMessage message) {

	}
	
	

	@Override
	protected boolean canRescue() {
		return true;
	}

	@Override
	public List<MessageType> getMessagesToListen() {
		List<MessageType> types;

		types = new ArrayList<MessageType>();
		types.add(MessageType.AgentIsBuriedMessage);
		types.add(MessageType.CivilianInformationMessage);
		types.add(MessageType.CivilianIsSavedOrDeadMessage);
		types.add(MessageType.BuildingIsExploredMessage);
		types.add(MessageType.BuildingIsBurningMessage);
		types.add(MessageType.BuildingIsExtinguishedMessage);
		types.add(MessageType.CivilianIsSavedOrDeadMessage);
		types.add(MessageType.CivilianIsSavedOrDeadMessage);
		return types;
	}

	@Override
	protected void act(int time, ChangeSet changed, Collection<Command> heard) {


		// //////////////////////////////////////

		updateUnexploredBuildings(changed);
		// Am I transporting a civilian to a refuge?
		if (someoneOnBoard()) {
			// Am I at a refuge?
			if (location() instanceof Refuge) {
				// Unload!
				Logger.info("Unloading");
				sendUnload(time);
				return;
			} else {
				// Move to a refuge
				Path path = getPathToRefuge();
				// search.breadthFirstSearch(me().getPosition(), refugeIDs);
				if (path != null) {
					Logger.info("Moving to refuge");
					sendMove(path);
					return;
				}
				// What do I do now? Might as well carry on and see if we can
				// dig someone else out.
				Logger.debug("Failed to plan path to refuge");
			}
		}


		//////////////////////////////////////////////////////////////////////////////////////
		/**
		 *  AT 发送求救信号 
		 */
		//TODO： Agent发消息之后删除之，以免重复
		System.out.print(this.isStuck(getMeAsHuman(), true) + "if"
				+ lastPosition + "AT -> 我在找 发送被堵信息   :" + this.location()
				+ "\n");
		if (this.isStuck(getMeAsHuman(), true)
				&& this.location() == lastPosition)// this.location().equals(this.positionHistory))
		{
			System.out.print("AT -> [SEND]i'M sTUCK~!!! 发送被堵信息\n");
			AgentIsStuckMessage message = new AgentIsStuckMessage(this
					.location().getID());
			this.sendMessage(message, MessagePriority.Medium);
		}
		lastPosition = this.location();
		// ///////////////////////////////////////////////////////////////////////////////////////

		for (Human next : getTargets()) {
			if (next.getPosition().equals(location().getID())) {
				// Targets in the same place might need rescueing or loading
				if ((next instanceof Civilian) && next.getBuriedness() == 0
						&& !(location() instanceof Refuge)
						&& !(location() instanceof Road)) {
					// Load
					Logger.info("Loading " + next);
					sendLoad(time, next.getID());
					//TODO :发布远程消息，通知远程删除
					tryDelete(next.getID()); //本地删除
					return;
				}
				if (next.getBuriedness() > 0) {
					// Rescue

					super.work=true;

					Logger.info("RgetTargetsescueing " + next);
					sendRescue(time, next.getID());
					//TODO :发布远程消息，通知远程删除
					tryDelete(next.getID()); //本地删除
					return;
					
				}

			}
			
			else {

				super.act(time, changed, heard);

				// Try to move to the target
				try {
					if (!next.isPositionDefined())
						continue;
					if (next.getPosition() == null)
						continue;
					if (worldmodel.getEntity(next.getPosition()) instanceof Refuge)
						continue;
					if (next.isBuriednessDefined())
						if (next.getBuriedness() == 0)
							continue;
					Path path = search.getPath(me(), next,
							PathType.LowBlockRepair);
					// search.breadthFirstSearch(me().getPosition(),
					// next.getPosition());
					if (path != null && path.isPassable()) {
						Logger.info("Moving to target");
						sendMove(path);

						return;
					}
				} catch (Exception e) {
				}
			}
		}
		
		// Path path1 = null;
		// if (!buildingwithhuman.isEmpty()) {
		// path1 = getSearch().getPath(me(), buildingwithhuman,
		// PathType.EmptyAndSafe);
		// if (path1 != null) {
		// if (path1.isPassable()) {
		//
		// sendMove(path1);
		// return;
		// }
		// }
		// }

		// Nothing to do
		// if(searchTarget!=null&&this.location().getID().getValue()==searchTarget.getValue()){
		// searchTarget=null;
		// }
		// if(searchTarget==null){
		// List<Building> b1 = new ArrayList<Building>();
		// // =new ArrayList<Building>;
		// for (EntityID e : unexploredBuildings1) {
		// StandardEntity s = world.getEntity(e);
		// b1.add((Building) s);
		// }
		// Path path = search.getPath(me(), b1, PathType.LowBlockRepair);
		// searchTarget=path.getDestination();
		// // (me().getPosition(),unexploredBuildings);
		// if (path != null) {
		// Logger.info("Searching buildings");
		// System.out.println("time"+time+"  "+me().getID()+"AT"+"Search newbuildings");
		// sendMove(path);
		// return;
		// }
		// }else{
		// StandardEntity e=this.world.getEntity(searchTarget);
		// if(e instanceof Building){
		// Building b=(Building)e;
		// Path path=search.getPath(me(), b, PathType.LowBlockRepair);
		// if (path != null) {
		// Logger.info("Searching buildings");
		// System.out.println("time"+time+"  "+me().getID()+"AT"+"Search oldbuildings");
		// sendMove(path);
		// return;
		// }
		// }
		//
		// }
		// System.out.println("   time   "+timeStep+"    ");
		
		/**
		 * @author iorange
		 * 在无人可救的情况下，搜素targets列表，走向远一些的target
		 * TODO:是否需要将当前target和远程target混合在一起呢？
		 *      不得而知。。
		 */
		if(!targets.isEmpty())
		{
			eliminateTargets();
			Path path = getPathTo(targets.get(0).getLocId(),
					PathType.LowBlockRepair);
			// search.breadthFirstSearch(me().getPosition(),
			// next.getPosition());
			if (path != null && path.isPassable()) {
				Logger.info("Moving to target far away..");
				System.out.println("AT->移动到远端地点 " + path.getDestination().toString());
				sendMove(path);

				return;
			}
		}
		
		Logger.info("Moving randomly");
		List<EntityID> e = getRandomWalk();
		sendMove(time, e);
	}

	// @Override
	// protected EnumSet<StandardEntityURN> getRequestedEntityURNsEnum() {
	// return EnumSet.of(StandardEntityURN.AMBULANCE_TEAM);
	// }

	private boolean someoneOnBoard() {
		for (StandardEntity next : model
				.getEntitiesOfType(StandardEntityURN.CIVILIAN)) {
			if (((Human) next).getPosition().equals(getID())) {
				Logger.debug(next + " is on board");
				return true;
			}
		}
		return false;
	}

	private List<Human> getTargets() {
		List<Human> targets = new ArrayList<Human>();
		for (StandardEntity next : model.getEntitiesOfType(
				StandardEntityURN.CIVILIAN, StandardEntityURN.FIRE_BRIGADE,
				StandardEntityURN.POLICE_FORCE,
				StandardEntityURN.AMBULANCE_TEAM)) {
			Human h = (Human) next;
			if (h == me()) {
				continue;
			}
			if (h.isHPDefined() && h.isBuriednessDefined()
					&& h.isDamageDefined() && h.isPositionDefined()
					&& h.getHP() > 0
					&& (h.getBuriedness() >= 0 || h.getDamage() > 0)) {

				targets.add(h);
			}
		}
		Collections.sort(targets, new DistanceSorter(location(), model));

		return targets;
	}

	private List<Building> getbuildingwithHuman() {
		List<Building> b1 = new ArrayList<Building>();
		for (Human h : getTargets()) {
			StandardEntity position = null;
			position = h.getPosition(getModel());
			if (position instanceof Building && (!(position instanceof Refuge))) {
				Building b = (Building) position;
				b1.add(b);

			}
		}
		return b1;
	}

	private void updateUnexploredBuildings(ChangeSet changed) {
		for (EntityID next : changed.getChangedEntities()) {
			unexploredBuildings1.remove(next);
		}
	}
	
	public void eliminateTargets()
	/**
	 * @author iorange
	 * 筛选信息，剔除不必要的或者超过限制的ATTarget.
	 * 附带筛选结果
	 */
	{
		Collections.sort(targets);
		int size = targets.size();
		if(size > 11)
		{
			//剔了！
			for(int i=size-1; i>=11; i--)
			{
				ATTarget ass = targets.remove(i);
				System.out.println("AT->删除过多的任务" 
							+ ass.toString()
						);
			}
		}
	}
	
	public void tryDelete(EntityID humanID)
	{
		/** @author iorange
		 * 如果已经开始施救，则从targets中清除（如果有）
		 * 
		 */
		for(ATTarget t : targets)
		{
			
			if(humanID.equals(t.getHumanId()))
			{
				System.out.println("AT try delete " + targets.size());
				targets.remove(t);
				System.out.println("AT delete result " + targets.size());
			}
		}
	}
}