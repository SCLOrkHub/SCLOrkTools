#ifndef SRC_CONFAB_RECORD_HPP_
#define SRC_CONFAB_RECORD_HPP_

#include "SizedPointer.hpp"

#include <memory>

namespace Confab {

/*! Storage class for non-owning pointer backing stores return results.
 *
 * Provides read-only access to underlying data store without copying the data to a separate buffer. Is virtual so that
 * different deletion needs can be applied for Database or Network specific objects but keep the overall APIs clean of
 * those details.
 */
class Record {
public:
    virtual ~Record() = default;

    /*! True if this Record has no results.
     *
     * \return A boolean which is true if this Record is pointing at nothing.
     */
    virtual bool empty() const = 0;

    /*! A pointer to the data associated with this record.
     *
     * \return A non-owning pointer to the data. Record will take care of the deletion of this pointer.
     */
    virtual const SizedPointer data() const = 0;

    /*! The key associated with this Record, if exists.
     *
     * \return A non-owning pointer to the key data.
     */
    virtual const SizedPointer key() const = 0;
};

using RecordPtr = std::shared_ptr<Record>;

/*! Simple override of Record class to provide an empty Record entry.
 */
class EmptyRecord : public Record {
public:
    ~EmptyRecord() override = default;

    /*! Always reports an empty Record.
     * \return Always true.
     */
    bool empty() const override { return true; }

    /*! Returns an empty data pointer.
     * \return Always empty pointer.
     */
    const SizedPointer data() const override { return SizedPointer(); }

    /*! Returns an empty key pointer.
     * \return Always empty key pointer.
     */
    const SizedPointer key() const override { return SizedPointer(); }
};

/*! Convenience routine to quickly construct an always empty RecordPtr.
 * \return A new EmptyRecord.
 */
RecordPtr makeEmptyRecord() {
    return std::shared_ptr<new EmptyRecord()>;
}

}  // namespace Confab

#endif  // SRC_CONFAB_RECORD_HPP_
