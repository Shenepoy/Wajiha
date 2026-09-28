package com.wajiha.domain.repository

import com.wajiha.data.db.CollectionCountRow
import com.wajiha.data.db.CollectionDao
import com.wajiha.data.db.CollectionEntity
import com.wajiha.data.db.CollectionGameCrossRef
import com.wajiha.data.db.GameEntity
import kotlinx.coroutines.flow.Flow

class CollectionRepository(
    private val collectionDao: CollectionDao,
) {
    fun observeAll(): Flow<List<CollectionEntity>> = collectionDao.observeAll()

    fun observeCounts(): Flow<List<CollectionCountRow>> = collectionDao.observeCounts()

    fun observeCollectionIds(gameId: Long): Flow<List<Long>> = collectionDao.observeCollectionIds(gameId)

    fun observeGames(collectionId: Long): Flow<List<GameEntity>> = collectionDao.observeGames(collectionId)

    suspend fun create(
        name: String,
        sortIndex: Int = 0,
    ): Long = collectionDao.insert(CollectionEntity(name = name, sortIndex = sortIndex))

    suspend fun delete(id: Long) {
        collectionDao.removeAllGames(id)
        collectionDao.delete(id)
    }

    suspend fun addGame(
        collectionId: Long,
        gameId: Long,
        position: Int = 0,
    ) = collectionDao.addGame(CollectionGameCrossRef(collectionId, gameId, position))

    suspend fun removeGame(
        collectionId: Long,
        gameId: Long,
    ) = collectionDao.removeGame(collectionId, gameId)
}
