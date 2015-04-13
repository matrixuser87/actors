package com.offbynull.peernetic.gateways.visualizer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.DoubleBinding;
import static javafx.beans.binding.DoubleExpression.doubleExpression;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.SceneAntialiasing;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Translate;
import javafx.stage.Stage;
import org.apache.commons.collections4.BidiMap;
import org.apache.commons.collections4.MultiMap;
import org.apache.commons.collections4.bidimap.DualHashBidiMap;
import org.apache.commons.collections4.map.MultiValueMap;
import org.apache.commons.collections4.map.UnmodifiableMap;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.ImmutablePair;

public final class GraphApplication extends Application {

    private static volatile GraphApplication instance;
    private static final Lock lock = new ReentrantLock();
    private static final Condition startedCondition = lock.newCondition();
    private static final Condition stoppedCondition = lock.newCondition();
    
    private final BidiMap<String, Label> nodes = new DualHashBidiMap<>();
    private final BidiMap<ImmutablePair<String, String>, Line> edges = new DualHashBidiMap<>();
    private final MultiMap<Label, Line> anchors = new MultiValueMap<>();
    private Group graph;
    
    private final UnmodifiableMap<Class<?>, Function<Object, Runnable>> runnableGenerators;

    public GraphApplication() {
        Map<Class<?>, Function<Object, Runnable>> generators = new HashMap<>();
        
        generators.put(AddNode.class, this::generateAddNodeCode);
        generators.put(MoveNode.class, this::generateMoveNodeCode);
        generators.put(StyleNode.class, this::generateStyleNodeCode);
        generators.put(RemoveNode.class, this::generateRemoveNodeCode);
        generators.put(AddEdge.class, this::generateAddEdgeCode);
        generators.put(StyleEdge.class, this::generateStyleEdgeCode);
        generators.put(RemoveEdge.class, this::generateRemoveEdgeCode);
        
        runnableGenerators = (UnmodifiableMap<Class<?>, Function<Object, Runnable>>) UnmodifiableMap.unmodifiableMap(generators);
    }

    @Override
    public void init() throws Exception {
        Platform.setImplicitExit(true);
        
        lock.lock();
        try {
            instance = this;
            startedCondition.signalAll();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void start(Stage stage) {
        graph = new Group();
        
        stage.setTitle("Graph");
        stage.setWidth(700);
        stage.setHeight(700);
        
        // We have to do this because in some cases JavaFX threads won't shut down when the last stage closes, even if implicitExit is set
        // http://stackoverflow.com/questions/15808063/how-to-stop-javafx-application-thread/22997736#22997736
        stage.setOnCloseRequest(x -> Platform.exit());


        Scene scene = new Scene(graph, 1.0, 1.0, true, SceneAntialiasing.DISABLED);
        scene.setFill(Color.WHITESMOKE);
        stage.setScene(scene);
        
        InvalidationListener invalidationListener = (x) -> {
            double scaleX = scene.getWidth() / graph.getLayoutBounds().getWidth();
            double scaleY = scene.getHeight() / graph.getLayoutBounds().getHeight();
            graph.getTransforms().clear();
            graph.getTransforms().add(new Scale(scaleX, scaleY, 0.0, 0.0));
            graph.getTransforms().add(new Translate(-graph.getLayoutBounds().getMinX(), -graph.getLayoutBounds().getMinY()));
        };
        
        graph.layoutBoundsProperty().addListener(invalidationListener);
        scene.widthProperty().addListener(invalidationListener);
        scene.heightProperty().addListener(invalidationListener);
        
        stage.show();
    }

    @Override
    public void stop() throws Exception {
        lock.lock();
        try {
            instance = null;
            stoppedCondition.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public static GraphApplication getInstance() {
        lock.lock();
        try {
            return instance;
        } finally {
            lock.unlock();
        }
    }
    
    public static GraphApplication awaitStarted() throws InterruptedException {
        lock.lock();
        try {
            if (instance == null) {
                startedCondition.await();
            }
            return instance;
        } finally {
            lock.unlock();
        }
    }

    public static void awaitStopped() throws InterruptedException {
        lock.lock();
        try {
            if (instance != null) {
                stoppedCondition.await();
            }
        } finally {
            lock.unlock();
        }
    }
    
    void execute(Collection<Object> commands) {
        Validate.notNull(commands);
        Validate.noNullElements(commands);
        
        List<Runnable> runnables = new ArrayList<>(commands.size());
        for (Object command : commands) {
            Function<Object, Runnable> func = runnableGenerators.get(command.getClass());
            if (func != null) {
                Runnable runnable = func.apply(command);
                runnables.add(runnable);
            } else {
                // TODO log here
                continue;
            }
        }
        
        Platform.runLater(() -> {
            for (Runnable runnable : runnables) {
                try {
                    runnable.run();
                } catch (RuntimeException re) {
                    // TODO log here
                }
            }
        });
    }
    
    private Runnable generateAddNodeCode(Object msg) {
        Validate.notNull(msg);
        AddNode addNode = (AddNode) msg;
        
        String id = addNode.getId();
        double x = addNode.getX();
        double y = addNode.getY();
        String style = addNode.getStyle();

        return () -> {
            Label label = new Label(id);

            label.layoutXProperty().set(x);
            label.layoutYProperty().set(y);
            label.setStyle(style);

            Label existingLabel = nodes.putIfAbsent(id, label);
            Validate.isTrue(existingLabel == null);

            graph.getChildren().add(label);
        };
    }

    private Runnable generateMoveNodeCode(Object msg) {
        Validate.notNull(msg);
        MoveNode moveNode = (MoveNode) msg;
        
        String id = moveNode.getId();
        double x = moveNode.getX();
        double y = moveNode.getY();

        return () -> {
            Label label = nodes.get(id);
            Validate.isTrue(label != null);
            label.layoutXProperty().set(x);
            label.layoutYProperty().set(y);
        };
    }
    
    private Runnable generateStyleNodeCode(Object msg) {
        Validate.notNull(msg);
        StyleNode styleNode = (StyleNode) msg;
        
        String id = styleNode.getId();
        String style = styleNode.getStyle();

        return () -> {
            Label label = nodes.get(id);
            Validate.isTrue(label != null);
            
            label.setStyle(style);
        };
    }

    @SuppressWarnings("unchecked")
    private Runnable generateRemoveNodeCode(Object msg) {
        Validate.notNull(msg);
        RemoveNode removeNode = (RemoveNode) msg;
        
        String id = removeNode.getId();

        return () -> {
            Label label = nodes.get(id);
            Validate.isTrue(label != null);
            graph.getChildren().remove(label);

            Collection<Line> lines = (Collection<Line>) anchors.remove(label);
            for (Line line : lines) {
                edges.removeValue(line);
                graph.getChildren().remove(line);
            }
        };
    }

    private Runnable generateAddEdgeCode(Object msg) {
        Validate.notNull(msg);
        AddEdge addEdge = (AddEdge) msg;
        
        String fromId = addEdge.getFromId();
        String toId = addEdge.getToId();

        return () -> {
            Label fromLabel = nodes.get(fromId);
            Label toLabel = nodes.get(toId);

            Validate.notNull(fromLabel);
            Validate.notNull(toLabel);
            
            Line line = new Line();
            DoubleBinding fromXBinding = doubleExpression(fromLabel.layoutXProperty())
                    .add(doubleExpression(fromLabel.widthProperty()).divide(2.0));
            DoubleBinding fromYBinding = doubleExpression(fromLabel.layoutYProperty())
                    .add(doubleExpression(fromLabel.heightProperty()).divide(2.0));
            DoubleBinding toXBinding = doubleExpression(toLabel.layoutXProperty())
                    .add(doubleExpression(toLabel.widthProperty()).divide(2.0));
            DoubleBinding toYBinding = doubleExpression(toLabel.layoutYProperty())
                    .add(doubleExpression(toLabel.heightProperty()).divide(2.0));
            line.startXProperty().bind(fromXBinding);
            line.startYProperty().bind(fromYBinding);
            line.endXProperty().bind(toXBinding);
            line.endYProperty().bind(toYBinding);

            ImmutablePair<String, String> key = new ImmutablePair<>(fromId, toId);
            Line existingLine = edges.putIfAbsent(key, line);
            Validate.isTrue(existingLine == null);

            anchors.put(fromLabel, line);
            anchors.put(toLabel, line);

            graph.getChildren().add(0, line);
        };
    }

    private Runnable generateStyleEdgeCode(Object msg) {
        Validate.notNull(msg);
        StyleEdge styleEdge = (StyleEdge) msg;
        
        String fromId = styleEdge.getFromId();
        String toId = styleEdge.getToId();
        String style = styleEdge.getStyle();

        return () -> {
            ImmutablePair<String, String> key = new ImmutablePair<>(fromId, toId);
            Line line = edges.get(key);
            Validate.isTrue(line != null);

            line.setStyle(style); // null is implicitly converted to an empty string
        };
    }
    
    private Runnable generateRemoveEdgeCode(Object msg) {
        Validate.notNull(msg);
        RemoveEdge removeEdge = (RemoveEdge) msg;
        
        String fromId = removeEdge.getFromId();
        String toId = removeEdge.getToId();

        return () -> {
            Label fromLabel = nodes.get(fromId);
            Label toLabel = nodes.get(toId);

            Validate.notNull(fromLabel);
            Validate.notNull(toLabel);

            ImmutablePair<String, String> key = new ImmutablePair<>(fromId, toId);
            Line line = edges.remove(key);
            Validate.isTrue(line != null);
            
            anchors.removeMapping(fromLabel, line);
            anchors.removeMapping(toLabel, line);

            graph.getChildren().remove(line);
        };
    }
}
