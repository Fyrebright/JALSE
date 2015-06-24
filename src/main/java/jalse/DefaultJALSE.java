package jalse;

import static jalse.actions.Actions.requireNotStopped;
import jalse.actions.Action;
import jalse.actions.ActionContext;
import jalse.actions.ActionEngine;
import jalse.actions.DefaultActionScheduler;
import jalse.actions.ForkJoinActionEngine;
import jalse.actions.ManualActionEngine;
import jalse.actions.MutableActionBindings;
import jalse.actions.MutableActionContext;
import jalse.actions.ThreadPoolActionEngine;
import jalse.attributes.AttributeContainer;
import jalse.entities.DefaultEntityContainer;
import jalse.entities.DefaultEntityFactory;
import jalse.entities.Entities;
import jalse.entities.Entity;
import jalse.entities.EntityContainer;
import jalse.entities.EntityFactory;
import jalse.entities.EntityListener;
import jalse.misc.AbstractIdentifiable;
import jalse.tags.Tag;
import jalse.tags.TagTypeSet;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * A {@link JALSE} implementation that using default implementations.<br>
 * <br>
 * DefaultJALSE uses {@link DefaultEntityFactory} (with no {@link Entity} limit) and
 * {@link ForkJoinActionEngine#commonPoolEngine()} by default if no {@link EntityFactory} or
 * {@link ActionEngine} are provided.<br>
 *
 * @author Elliot Ford
 *
 */
public class DefaultJALSE extends AbstractIdentifiable implements JALSE {

    /**
     * A {@link DefaultJALSE} instance builder that uses the defined {@link ActionEngine}
     * implementation with {@link DefaultEntityFactory}.<br>
     * <br>
     * By default this builder will throw {@link IllegalStateException} as the values must be built. <br>
     *
     * @author Elliot Ford
     *
     *
     * @see ForkJoinActionEngine
     * @see ThreadPoolActionEngine
     * @see ManualActionEngine
     *
     */
    public static final class Builder {

	private enum EngineType {

	    COMMON, FORKJOIN, MANUAL, NONE, THREADPOOL
	}

	private static final int MINIMUM_PARALLALISM = 1;

	private EngineType engineType;
	private UUID id;
	private int parallelism;
	private int totalEntityLimit;

	/**
	 * Creates a new Builder instance.
	 */
	public Builder() {
	    id = null;
	    parallelism = 0;
	    totalEntityLimit = 0;
	    engineType = EngineType.NONE;
	}

	/**
	 * Builds an instance of JALSE with the supplied parameters.
	 *
	 * @return Newly created JALSE instance.
	 */
	public DefaultJALSE build() throws IllegalStateException {
	    if (id == null) {
		throw new IllegalStateException("ID cannot be null");
	    }

	    if (totalEntityLimit < 1) {
		throw new IllegalStateException("Entity limit must be above one");
	    }

	    ActionEngine engine = null;
	    switch (engineType) {
	    case COMMON:
		engine = ForkJoinActionEngine.commonPoolEngine();
		break;
	    case MANUAL:
		engine = new ManualActionEngine();
		break;
	    case THREADPOOL:
		if (parallelism < MINIMUM_PARALLALISM) {
		    throw new IllegalStateException(String.format("Parallelism for ThreadPool must be %d or above",
			    MINIMUM_PARALLALISM));
		}
		engine = new ThreadPoolActionEngine(parallelism);
		break;
	    case FORKJOIN:
		if (parallelism < MINIMUM_PARALLALISM) {
		    throw new IllegalStateException(String.format("Parallelism for ForkJoin must be %d or above",
			    MINIMUM_PARALLALISM));
		}
		engine = new ForkJoinActionEngine(parallelism);
		break;
	    default: // Assume engineType = EngineType.NONE;
		throw new IllegalStateException("No engine selected");
	    }

	    return new DefaultJALSE(id, engine, new DefaultEntityFactory(totalEntityLimit));
	}

	/**
	 * Users the common engine based on the common pool.
	 *
	 * @return This builder.
	 *
	 * @see ForkJoinPool#commonPool()
	 * @see ForkJoinActionEngine#commonPoolEngine()
	 */
	public Builder setCommonPoolEngine() {
	    engineType = EngineType.COMMON;
	    return this;
	}

	/**
	 * Sets fork join engine to be used.
	 *
	 * @return This builder.
	 *
	 * @see ForkJoinActionEngine
	 */
	public Builder setForkJoinEngine() {
	    engineType = EngineType.FORKJOIN;
	    return this;
	}

	/**
	 * Sets the unique ID for JALSE instance.
	 *
	 * @param id
	 *            ID of JALSE.
	 * @return This builder.
	 */
	public Builder setID(final UUID id) {
	    this.id = id;
	    return this;
	}

	/**
	 * Sets the engine to be a manual tick engine.
	 *
	 * @return This builder.
	 */
	public Builder setManualEngine() {
	    engineType = EngineType.MANUAL;
	    return this;
	}

	/**
	 * Sets there to be no entity limit.
	 *
	 * @return This builder.
	 *
	 * @see Integer#MAX_VALUE
	 */
	public Builder setNoEntityLimit() {
	    return setTotalEntityLimit(Integer.MAX_VALUE);
	}

	/**
	 * Sets the parallelism to be utilised by the engine.
	 *
	 * @param parallelism
	 *            Thread parallelism.
	 * @return This builder.
	 */
	public Builder setParallelism(final int parallelism) {
	    this.parallelism = parallelism;
	    return this;
	}

	/**
	 * Sets the parallelism to the available processors.
	 *
	 * @return This builder.
	 *
	 * @see Runtime#availableProcessors()
	 */
	public Builder setParallelismToProcessors() {
	    return setParallelism(Runtime.getRuntime().availableProcessors());
	}

	/**
	 * Sets the ID to a random one.
	 *
	 * @return This builder.
	 *
	 * @see UUID#randomUUID()
	 */
	public Builder setRandomID() {
	    return setID(UUID.randomUUID());
	}

	/**
	 * Sets to use a single thread.
	 *
	 * @return This builder.
	 */
	public Builder setSingleThread() {
	    return setParallelism(1);
	}

	/**
	 * Sets thread pool engine to be used.
	 *
	 * @return This builder.
	 *
	 * @see ThreadPoolActionEngine
	 */
	public Builder setThreadPoolEngine() {
	    engineType = EngineType.THREADPOOL;
	    return this;
	}

	/**
	 * Sets the total entity limit parameter.
	 *
	 * @param totalEntityLimit
	 *            Maximum entity limited.
	 * @return This builder.
	 */
	public Builder setTotalEntityLimit(final int totalEntityLimit) {
	    this.totalEntityLimit = totalEntityLimit;
	    return this;
	}
    }

    /**
     * Creates a common pool DefaultJALSE instance (with a random ID and no entity limit).
     *
     * @return Default parallelism DefaultJALSE instance.
     *
     * @see Builder#setRandomID()
     * @see Builder#setCommonPoolEngine()
     * @see Builder#setNoEntityLimit()
     */
    public static DefaultJALSE buildCommonPoolWithDefaults() {
	return new Builder().setRandomID().setNoEntityLimit().setCommonPoolEngine().build();
    }

    /**
     * Builds a manually ticked DefaultJALSE instance (with a random ID and no entity limit).
     *
     * @return Manual tick DefaultJALSE.
     *
     * @see Builder#setRandomID()
     * @see Builder#setManualEngine()
     * @see Builder#setNoEntityLimit()
     */
    public static DefaultJALSE buildManualWithDefaults() {
	return new Builder().setRandomID().setNoEntityLimit().setManualEngine().build();
    }

    /**
     * Builds a single threaded DefaultJALSE instance (with a random ID and no entity limit).
     *
     * @return Single threaded DefaultJALSE instance.
     *
     * @see Builder#setRandomID()
     * @see Builder#setSingleThread()
     * @see Builder#setThreadPoolEngine()
     * @see Builder#setNoEntityLimit()
     */
    public static DefaultJALSE buildSingleThreadedWithDefaults() {
	return new Builder().setRandomID().setNoEntityLimit().setSingleThread().setThreadPoolEngine().build();
    }

    /**
     * Action engine to be supplied to entities.
     */
    protected final ActionEngine engine;

    /**
     * Backing entity container for top level entities.
     */
    protected final DefaultEntityContainer entities;

    /**
     * Entity factory for creating/killing entities.
     */
    protected final EntityFactory factory;

    /**
     * Self action scheduler.
     */
    protected final DefaultActionScheduler<JALSE> scheduler;

    /**
     * Current state information.
     */
    protected final TagTypeSet tags;

    /**
     * Creates a new instance of DefaultJALSE using the common pool engine and default entity
     * factory (no limit).
     *
     * @param id
     *            The ID used to identify between JALSE instances.
     */
    public DefaultJALSE(final UUID id) {
	this(id, ForkJoinActionEngine.commonPoolEngine());
    }

    /**
     * Creates a new instance of DefaultJALSE using the default entity factory (no limit).
     *
     * @param id
     *            The ID used to identify between JALSE instances.
     * @param engine
     *            Action engine to associate to factory and schedule actions.
     */
    public DefaultJALSE(final UUID id, final ActionEngine engine) {
	this(id, engine, new DefaultEntityFactory());
    }

    /**
     * Creates a new instance of DefaultJALSE with the supplied engine and factory.
     *
     * @param id
     *            The ID used to identify between JALSE instances.
     *
     * @param engine
     *            Action engine to associate to factory and schedule actions.
     * @param factory
     *            Entity factory to create/kill child entities.
     *
     */
    public DefaultJALSE(final UUID id, final ActionEngine engine, final EntityFactory factory) {
	super(id);
	this.engine = requireNotStopped(engine);
	this.factory = Objects.requireNonNull(factory);
	factory.setEngine(engine);
	scheduler = new DefaultActionScheduler<>(this);
	scheduler.setEngine(engine);
	entities = new DefaultEntityContainer(factory, this);
	tags = new TagTypeSet();
    }

    @Override
    public boolean addEntityListener(final EntityListener listener) {
	return entities.addEntityListener(listener);
    }

    @Override
    public void cancelAllScheduledForActor() {
	scheduler.cancelAllScheduledForActor();
    }

    @Override
    public MutableActionBindings getBindings() {
	return engine.getBindings();
    }

    @Override
    public Entity getEntity(final UUID id) {
	return entities.getEntity(id);
    }

    @Override
    public int getEntityCount() {
	return entities.getEntityCount();
    }

    @Override
    public Set<UUID> getEntityIDs() {
	return entities.getEntityIDs();
    }

    @Override
    public Set<? extends EntityListener> getEntityListeners() {
	return entities.getEntityListeners();
    }

    @Override
    public Set<UUID> getIDsInTree() {
	return Entities.getEntityIDsRecursively(entities);
    }

    @Override
    public Set<Tag> getTags() {
	return Collections.unmodifiableSet(tags);
    }

    @Override
    public int getTreeCount() {
	return Entities.getEntityCountRecursively(entities);
    }

    @Override
    public boolean isPaused() {
	return engine.isPaused();
    }

    @Override
    public boolean isStopped() {
	return engine.isStopped();
    }

    @Override
    public void killEntities() {
	entities.killEntities();
    }

    @Override
    public boolean killEntity(final UUID id) {
	return entities.killEntity(id);
    }

    @Override
    public <T> MutableActionContext<T> newContext(final Action<T> action) {
	return engine.newContext(action);
    }

    @Override
    public MutableActionContext<JALSE> newContextForActor(final Action<JALSE> action) {
	return scheduler.newContextForActor(action);
    }

    @Override
    public Entity newEntity(final UUID id, final AttributeContainer sourceContainer) {
	return entities.newEntity(id, sourceContainer);
    }

    @Override
    public <T extends Entity> T newEntity(final UUID id, final Class<T> type, final AttributeContainer sourceContainer) {
	return entities.newEntity(id, type, sourceContainer);
    }

    @Override
    public void pause() {
	engine.pause();
    }

    @Override
    public boolean receiveEntity(final Entity e) {
	return entities.receiveEntity(e);
    }

    @Override
    public boolean removeEntityListener(final EntityListener listener) {
	return entities.removeEntityListener(listener);
    }

    @Override
    public void removeEntityListeners() {
	entities.removeEntityListeners();
    }

    @Override
    public void resume() {
	engine.resume();
    }

    @Override
    public ActionContext<JALSE> scheduleForActor(final Action<JALSE> action, final long initialDelay,
	    final long period, final TimeUnit unit) {
	return scheduler.scheduleForActor(action, initialDelay, period, unit);
    }

    @Override
    public void stop() {
	engine.stop();
    }

    @Override
    public Stream<Entity> streamEntities() {
	return entities.streamEntities();
    }

    @Override
    public Stream<Entity> streamEntityTree() {
	return Entities.walkEntities(entities);
    }

    @Override
    public boolean transferEntity(final UUID id, final EntityContainer destination) {
	return entities.transferEntity(id, destination);
    }
}
