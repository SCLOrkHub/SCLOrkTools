#include "AssetData.hpp"

#include "schemas/FlatAssetData_generated.h"

#include "Constants.hpp"

#include <glog/logging.h>

namespace Confab {

AssetData::AssetData() :
    m_record(),
    m_flatAssetData(nullptr),
    m_builder(new flatbuffers::FlatBufferBuilder(4 * 1024)),
    m_assetDataBuilder(new Data::FlatAssetDataBuilder(*m_builder)),
    m_mutableFlatAssetData(nullptr) {
}

uint8_t* AssetData::setData(size_t size) {
    CHECK(m_builder);
    CHECK(m_assetDataBuilder);
    CHECK(size <= kDataChunkSize) << "data size over capacity.";

    uint8_t* space = nullptr;
    auto data = m_builder->CreateUninitializedVector(size, &space);
    m_assetDataBuilder->add_data(data);
    return space;
}

void AssetData::setHash(uint64_t hash) {
    CHECK(m_assetDataBuilder);
    m_assetDataBuilder->add_hash(hash);
}

const SizedPointer AssetData::flatten() {
    auto flatAssetData = m_assetDataBuilder->Finish();
    m_builder->Finish(flatAssetData);
    m_mutableFlatAssetData = Data::GetMutableFlatAssetData(m_builder->GetBufferPointer());
    return SizedPointer(m_builder->GetBufferPointer(), m_builder->GetSize());
}

void AssetData::changeHash(uint64_t hash) {
    CHECK(m_mutableFlatAssetData) << "need to call flatten() first";
    m_mutableFlatAssetData->mutate_hash(hash);
}

// static
const AssetData AssetData::LoadAssetData(const RecordPtr record) {
    auto flatAssetData = Data::GetFlatAssetData(record->data().data());
    return AssetData(record, flatAssetData);
}

AssetData::AssetData(const RecordPtr record, const Data::FlatAssetData* flatAssetData) :
    m_record(record),
    m_flatAssetData(flatAssetData),
    m_mutableFlatAssetData(nullptr) {
}

}  // namespace Confab

