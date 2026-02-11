package dev.hytalemodding.creditsystem.db;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import dev.hytalemodding.creditsystem.config.CreditConfig;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.Arrays;
import java.util.Date;
import java.util.UUID;

import static com.mongodb.client.MongoClients.create;

public final class MongoCreditsRepository implements CreditsRepository {
    private final CreditConfig.MongoDbSettings settings;

    private MongoClient client;
    private MongoCollection<Document> collection;

    public MongoCreditsRepository(CreditConfig.MongoDbSettings settings) {
        this.settings = settings;
    }

    @Override
    public long getBalance(UUID uuid, String name) {
        Bson filter = Filters.eq("uuid", uuid.toString());
        Document doc = collection.find(filter).first();
        if (doc == null) {
            collection.updateOne(
                    filter,
                    new Document("$set", new Document("name", name)
                            .append("balance", 0L)
                            .append("updatedAt", new Date())
                            .append("uuid", uuid.toString())),
                    new UpdateOptions().upsert(true)
            );
            return 0L;
        }
        Number balance = doc.get("balance", Number.class);
        return balance == null ? 0L : balance.longValue();
    }

    @Override
    public void addCredits(UUID uuid, String name, long amount) {
        if (amount <= 0) {
            return;
        }
        Bson filter = Filters.eq("uuid", uuid.toString());
        collection.updateOne(
                filter,
                new Document("$set", new Document("name", name).append("updatedAt", new Date()))
                        .append("$setOnInsert", new Document("uuid", uuid.toString()).append("balance", 0L))
                        .append("$inc", new Document("balance", amount)),
                new UpdateOptions().upsert(true)
        );
    }

    @Override
    public void removeCredits(UUID uuid, String name, long amount) {
        if (amount <= 0) {
            return;
        }

        Bson filter = Filters.eq("uuid", uuid.toString());
        collection.updateOne(
                filter,
                Arrays.asList(
                        new Document("$set", new Document("uuid", uuid.toString())
                                .append("name", name)
                                .append("updatedAt", new Date())
                                .append("balance", new Document("$max", Arrays.asList(
                                        new Document("$add", Arrays.asList(new Document("$ifNull", Arrays.asList("$balance", 0L)), -amount)),
                                        0L
                                ))))
                ),
                new UpdateOptions().upsert(true)
        );
    }

    @Override
    public void setCredits(UUID uuid, String name, long amount) {
        long clamped = Math.max(0L, amount);
        Bson filter = Filters.eq("uuid", uuid.toString());
        collection.updateOne(
                filter,
                new Document("$set", new Document("uuid", uuid.toString())
                        .append("name", name)
                        .append("balance", clamped)
                        .append("updatedAt", new Date())),
                new UpdateOptions().upsert(true)
        );
    }

    @Override
    public void initialize() {
        this.client = create(settings.uri());
        MongoDatabase db = client.getDatabase(settings.database());
        this.collection = db.getCollection("credits");
        this.collection.createIndex(new Document("uuid", 1));
    }

    @Override
    public void shutdown() {
        if (client != null) {
            client.close();
        }
    }

    @Override
    public String backendName() {
        return "mongodb";
    }

    @Override
    public String location() {
        return settings.uri() + "/" + settings.database();
    }
}
